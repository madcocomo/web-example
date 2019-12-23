package io.vertx.example.web.fizzbuzz;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.example.util.Runner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

import java.text.DateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link io.vertx.core.Verticle} which implements a simple, realtime,
 * multiuser chat. Anyone can connect to the chat application on port
 * 8000 and type messages. The messages will be rebroadcast to all
 * connected users via the @{link EventBus} Websocket bridge.
 *
 * @author <a href="https://github.com/InfoSec812">Deven Phillips</a>
 */
public class Server extends AbstractVerticle {

  public static final int AUTO_PLAYER_SPEED = 2000;

  // Convenience method so you can run it in your IDE
  public static void main(String[] args) {
    Runner.runExample(Server.class);
  }

  @Override
  public void start() throws Exception {

    Router router = Router.router(vertx);

    // Allow events for the designated addresses in/out of the event bus bridge
    BridgeOptions opts = new BridgeOptions()
      .addInboundPermitted(new PermittedOptions().setAddress("chat.to.server"))
      .addOutboundPermitted(new PermittedOptions().setAddress("chat.to.client"));

    // Create the event bus bridge and add it to the router.
    SockJSHandler ebHandler = SockJSHandler.create(vertx);
    ebHandler.bridge(opts);
    router.route("/eventbus/*").handler(ebHandler);

    // Create a router endpoint for the static content.
    router.route().handler(StaticHandler.create());

    // Start the web server and tell it to use the router to handle requests.
    vertx.createHttpServer().requestHandler(router).listen(8080);

    EventBus eb = vertx.eventBus();
    AtomicInteger counter = new AtomicInteger(1);

    // Register to listen for messages coming IN to the server
    eb.consumer("chat.to.server").handler(message -> {
      String input = message.body().toString();
      if (Integer.parseInt(input) != counter.get()) {
        eb.publish("chat.to.client", withTimeStamp("You are wrong"));
      } else {
        counter.incrementAndGet();
        eb.publish("chat.to.client", withTimeStamp("player said: " + input));
      }
    });



    vertx.setPeriodic(AUTO_PLAYER_SPEED, t -> {
      int i = counter.get();
      if (i % 7 == 0) {
        eb.publish("chat.to.client", withTimeStamp("server said: waiting"));
      } else {
        eb.publish("chat.to.client", withTimeStamp("server said: " + counter.getAndIncrement()));
      }
    });
  }

  private String withTimeStamp(String body) {
    String timestamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date.from(Instant.now()));
    return timestamp + ": " + body;
  }
}
