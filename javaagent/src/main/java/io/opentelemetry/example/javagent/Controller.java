package io.opentelemetry.example.javagent;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

  private static final Logger LOGGER = LogManager.getLogger(Controller.class);
  private final AttributeKey<String> ATTR_METHOD = AttributeKey.stringKey("method");

  private final Random random = new Random();
  private final Tracer tracer;
  private final LongHistogram doWorkHistogram;

  @Autowired
  Controller(OpenTelemetry openTelemetry) {
    tracer = openTelemetry.getTracer(Application.class.getName());
    Meter meter = openTelemetry.getMeter(Application.class.getName());
    doWorkHistogram = meter.histogramBuilder("do-work").ofLongs().build();
  }

  @GetMapping("/plain")
  public String plain() throws InterruptedException {
    int sleepTime = random.nextInt(200);

    Thread.sleep(sleepTime);

    return "plain";
  }

  @GetMapping("/ping")
  public String ping() throws InterruptedException {
    int sleepTime = random.nextInt(200);
    doWork("doSingleWork", 0, sleepTime);
    doWorkHistogram.record(sleepTime, Attributes.of(ATTR_METHOD, "ping"));
    return "pong";
  }

  @GetMapping("/zig")
  public String zig() throws InterruptedException {
    int sleepTime = random.nextInt(200);
    int loopCount = 2 + random.nextInt(10);

    for (int i = 0; i < loopCount; i++) {
      doWork("doWorkAt-" + i, i, sleepTime);
    }
    return "zag";
  }

  private void doWork(String spanName, int loopCount, int sleepTime) throws InterruptedException {
    Span span = tracer.spanBuilder(spanName).startSpan();

    span.setAttribute("my-loop-count", loopCount);
    span.setAttribute("my-random-val", random.nextInt(100));
    // add day of week to the span
    LocalDate today = LocalDate.now();
    DayOfWeek dayOfWeek = today.getDayOfWeek();
    span.setAttribute("my-day-of-week", dayOfWeek.toString());

    try (Scope ignored = span.makeCurrent()) {
      Thread.sleep(sleepTime);
      LOGGER.info("A sample log message!");
    } finally {
      span.end();
    }
  }
}
