package xdean.fastfxml.benchmark;

import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import com.sun.javafx.application.PlatformImpl;

import xdean.fastfxml.FXMLLoader;

public class FXMLLoaderBenchmark {
  static {
    PlatformImpl.startup(() -> {
    });
  }

  public static void main(String[] args) throws Exception {
    Options options = new OptionsBuilder()
        .include(FXMLLoaderBenchmark.class.getName() + ".*")
        .mode(Mode.AverageTime)
        .warmupTime(TimeValue.seconds(1))
        .warmupIterations(2)
        .threads(1)
        .measurementIterations(5)
        .measurementTime(TimeValue.seconds(1))
        .forks(1)
        .shouldFailOnError(true)
        .shouldDoGC(true)
        .build();

    new Runner(options).run();
  }

  @Benchmark
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  public void fastFxml() throws Exception {
    URL u = FXMLLoaderBenchmark.class.getResource("simple.fxml");
    for (int i = 0; i < 100; i++) {
      FXMLLoader fxmlLoader = new FXMLLoader(u);
      fxmlLoader.load();
    }
  }

  @Benchmark
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  public void openjfxFxml() throws Exception {
    URL u = FXMLLoaderBenchmark.class.getResource("simple.fxml");
    for (int i = 0; i < 100; i++) {
      javafx.fxml.FXMLLoader fxmlLoader = new javafx.fxml.FXMLLoader(u);
      fxmlLoader.load();
    }
  }
}
