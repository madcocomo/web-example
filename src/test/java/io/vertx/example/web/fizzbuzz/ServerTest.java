package io.vertx.example.web.fizzbuzz;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.anyOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class ServerTest {
  @Test
  void countThreeTicks(Vertx vertx, VertxTestContext testContext) {
    AtomicInteger counter = new AtomicInteger();
    vertx.setPeriodic(100, id -> {
      if (counter.incrementAndGet() == 3) {
        testContext.completeNow();
      }
    });
    assertEquals(0, counter.get());
  }

  @Test
  @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
  void countThreeTicksWithCheckpoints(Vertx vertx, VertxTestContext testContext) {
    Checkpoint checkpoint = testContext.checkpoint(2);
    vertx.setTimer(100, id -> checkpoint.flag());
    vertx.setTimer(200, id -> checkpoint.flag());
  }

  @Test
  @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
  void useServerVerticle(Vertx vertx, VertxTestContext testContext) {
    Checkpoint deploymentCheckpoint = testContext.checkpoint();
    vertx.deployVerticle(new Server(), testContext.succeeding(id -> {
      WebClient webClient = WebClient.create(vertx);
      webClient.get(8080, "localhost", "/eventbus/info")
        .send(testContext.succeeding(resp -> {
          testContext.verify(() -> {
            assertEquals(200, resp.statusCode());
            deploymentCheckpoint.flag();
          });
        }));
      Checkpoint eventCheckpoint = testContext.checkpoint(1);
      EventBus bus = vertx.eventBus();
      bus.consumer("chat.to.client").handler(message -> {
        testContext.verify(() -> {
            assertThat(message.body().toString()).endsWith("1");
            eventCheckpoint.flag();
          }
        );
      });
    }));
  }

}
