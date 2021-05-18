package com.kubukoz.demo

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import cats.Functor
import cats.effect.Effect
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.effect.concurrent.Ref
import cats.effect.implicits._
import cats.implicits._
import cats.~>
import io.circe.Codec
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaHttpServerOptions
import sttp.tapir.swagger.akkahttp.SwaggerAkka

import scala.concurrent.Future

object Demo extends IOApp.Simple {
  val mkLogic: IO[UserLogic[IO]] = Ref[IO].of(List.empty[User]).map { userRef =>
    new UserLogic[IO] {
      def findUsers: IO[List[User]] = userRef.get
      def createUser(user: User): IO[Unit] =
        IO(println(s"createUser($user)")) *> userRef.update(_ :+ user)
    }
  }

  val mkActorSystem: Resource[IO, ActorSystem] = Resource
    .make(IO(ActorSystem()))(as => IO.fromFuture(IO(as.terminate())).void)

  def mkServer(routes: Route)(implicit
      as: ActorSystem
  ): Resource[IO, Http.ServerBinding] =
    Resource.make(
      IO.fromFuture(IO(Http().newServerAt("0.0.0.0", 4000).bindFlow(routes)))
    )(srv => IO.fromFuture(IO(srv.unbind())).void)

  val run: IO[Unit] = mkLogic.flatMap { logic =>
    val route = AkkaRouter.combineAll(List(UserRouter.instance(logic)))(
      AkkaHttpServerOptions.default
    )

    mkActorSystem
      .flatMap { implicit as => mkServer(route) }
      .use(_ => IO.never)
  }
}

object AkkaRouter {
  def combineAll[F[_]: Effect](
      routers: List[Router[F]]
  )(config: AkkaHttpServerOptions): Route = {
    import sttp.tapir._

    import sttp.tapir.server.akkahttp._
    import sttp.tapir.integ.cats.syntax._
    import akka.http.scaladsl.server.Directives._

    val fToFuture: F ~> Future = Effect
      .toIOK[F]
      .andThen(new (IO ~> Future) {
        def apply[A](fa: IO[A]): Future[A] = fa.unsafeToFuture()
      })

    // Note: when this is called, the Future is already running - but this is fine in this case because it's only going to happen in tapir's MonadError
    val futureToF: Future ~> F = new (Future ~> F) {
      def apply[A](fa: Future[A]): F[A] = ???
    }

    val allEndpoints = routers.flatMap(_.endpoints)

    val baseRoute = AkkaHttpServerInterpreter.toRoute(
      allEndpoints
        .map(_.imapK[Future](fToFuture)(futureToF))
    )

    val swaggerRoute = {
      import sttp.tapir.openapi.OpenAPI
      import sttp.tapir.docs.openapi._

      val openapi = OpenAPIDocsInterpreter.serverEndpointsToOpenAPI(
        allEndpoints,
        "demo app",
        "1.0.0"
      )

      import sttp.tapir.openapi.circe.yaml._

      new SwaggerAkka(openapi.toYaml).routes
    }

    baseRoute ~ swaggerRoute
  }
}

trait Router[F[_]] {
  def endpoints: List[ServerEndpoint[_, _, _, Any, F]]
}

object UserRouter {
  def instance[F[_]: Functor](logic: UserLogic[F]): Router[F] =
    new Router[F] {
      def endpoints: List[ServerEndpoint[_, _, _, Any, F]] = {
        import sttp.tapir._
        import sttp.tapir.json.circe._
        import sttp.tapir.generic.auto._

        val baseEndpoint = endpoint.in("users")

        List(
          baseEndpoint.get
            .out(jsonBody[List[User]])
            .serverLogic(_ => logic.findUsers.map(Right(_))),
          baseEndpoint.post
            .in(jsonBody[User])
            .serverLogic(user => logic.createUser(user).as(Right(())))
        )
      }
    }
}

final case class User(name: String)
object User {
  implicit val codec: Codec.AsObject[User] =
    Codec.forProduct1("name")(User(_))(_.name)
}

trait UserLogic[F[_]] {
  def findUsers: F[List[User]]
  def createUser(user: User): F[Unit]
}
