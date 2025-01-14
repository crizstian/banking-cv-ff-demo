package io.harness.cvdemo.metrics;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import com.google.common.annotations.VisibleForTesting;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Summary;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;

/**
 * Harness Metric Registry is a custom Metric Registry build on the top of
 * CodeHale MetricRegistry Any Codehale metric that needs to be projected has to
 * be registered here This metric Registry supports all kind of Metrics by
 * DropWizard To register custom Metric use specific register method. Or if want
 * to register metrics by annotation that can be done directly by annotations.
 *
 * Created by Pranjal on 11/01/2018
 */
@Slf4j
@NoArgsConstructor
@Singleton
public class CVDemoMetricsRegistry {
  // DropWizard metric registry that stores the metrics
  private MetricRegistry metricRegistry;
  private MetricRegistry threadPoolMetricRegistry;

  // Prometheus Collector Registry used for exposing metrics to prometheus by
  // rest endpoint
  private CollectorRegistry collectorRegistry;

  private final Map<String, Object> namesToCollectors = new HashMap<>();
  private final Map<String, Object> codahaleNamesToCollectors = new HashMap<>();

  // Default metric path for Custom Metrics
  // Any new custom metric that needs to be registered should have this path as
  // prefix. In the code
  private static final String DEFAULT_METRIC_PATH_PREFIX =
      "io_harness_custom_metric_";

  public CVDemoMetricsRegistry(MetricRegistry metricRegistry,
                               CollectorRegistry collectorRegistry) {
    this.metricRegistry = metricRegistry;
    this.collectorRegistry = collectorRegistry;
    this.threadPoolMetricRegistry = new MetricRegistry();
    // this.collectorRegistry.register(new
    // CustomDropWizardExports(metricRegistry));
  }

  public MetricRegistry getMetricRegistry() { return metricRegistry; }
  public MetricRegistry getThreadPoolMetricRegistry() {
    return threadPoolMetricRegistry;
  }

  public void registerCounterMetric(String metricName, String[] labels,
                                    String doc) {
    String name = getAbsoluteMetricName(metricName);
    Counter.Builder builder = Counter.build().name(name).help(doc);
    if (labels != null) {
      builder.labelNames(labels);
    }
    if (doc != null) {
      builder.help(doc);
    } else {
      builder.help(metricName);
    }
    Counter metric = builder.create();

    com.codahale.metrics.Counter counterMetric = metricRegistry.counter(name);

    codahaleNamesToCollectors.put(name, counterMetric);

    collectorRegistry.register(metric);

    namesToCollectors.put(name, metric);
  }

  public void registerGaugeMetric(String metricName, String[] labels,
                                  String doc) {
    String name = getAbsoluteMetricName(metricName);
    Gauge.Builder builder = Gauge.build().name(name).help(doc);
    if (labels != null) {
      builder.labelNames(labels);
    }
    if (doc != null) {
      builder.help(doc);
    } else {
      builder.help(metricName);
    }
    Gauge metric = builder.create();

    collectorRegistry.register(metric);
    namesToCollectors.put(name, metric);
  }



  public void registerMeteredMetric(String metricName) {
    String name = getAbsoluteMetricName(metricName);
    Meter metric = metricRegistry.meter(name);
    namesToCollectors.put(name, metric);
  }

  public void registerSummaryMetric(String metricName) {
    String name = getAbsoluteMetricName(metricName);
    Summary metric2 = Summary.build()
                          .name(name)
                          .help(metricName)
                          .quantile(0.5, 0.1)
                          .create()
                          .register(collectorRegistry);
    namesToCollectors.put(name, metric2);
  }

  public void registerHistogramMetric(String metricName,
                                      Histogram.Builder builder) {
    String name = getAbsoluteMetricName(metricName);
    Histogram metric = builder.create();
    collectorRegistry.register(metric);
    namesToCollectors.put(name, metric);
  }

  public void recordCounterInc(String metricName, String... labelValues) {
    Counter metric =
        (Counter)namesToCollectors.get(getAbsoluteMetricName(metricName));
    if (metric != null) {
      if (labelValues != null) {
        metric.labels(labelValues).inc();
      } else {
        metric.inc();
      }
    }
    else{
      registerCounterMetric(metricName, labelValues, null);
      metric =
              (Counter)namesToCollectors.get(getAbsoluteMetricName(metricName));
      if (labelValues != null) {
        metric.labels(labelValues).inc();
      } else {
        metric.inc();
      }
    }
  }

  public void recordHistogram(String metricName, String[] labelValues,
                              double amount) {
    Histogram metric =
        (Histogram)namesToCollectors.get(getAbsoluteMetricName(metricName));
    if (metric != null) {
      if (labelValues != null) {
        metric.labels(labelValues).observe(amount);
      } else {
        metric.observe(amount);
      }
    }
  }

  public void recordGaugeInc(String metricName, String[] labelValues) {
    Gauge metric =
        (Gauge)namesToCollectors.get(getAbsoluteMetricName(metricName));

    if (metric != null) {
      if (labelValues != null) {
        metric.labels(labelValues).inc();
      } else {
        metric.inc();
      }
    }
    else{
      registerGaugeMetric(metricName, labelValues);
      metric = (Gauge)namesToCollectors.get(getAbsoluteMetricName(metricName));
      if (labelValues != null) {
        metric.labels(labelValues).set(0);
        metric.labels(labelValues).inc();
      } else {
        metric.set(0);
        metric.inc();
      }
    }
  }

  public void recordGaugeInc(String metricName, String label,String labelValues) {
    Gauge metric =
            (Gauge)namesToCollectors.get(getAbsoluteMetricName(metricName));

    if (metric != null) {
      if (labelValues != null) {
        metric.labels(labelValues).inc();
      } else {
        metric.inc();
      }
    }
    else{
      log.info("label to register: "+label);
      registerGaugeMetric(metricName, new String[]{label});
      metric = (Gauge)namesToCollectors.get(getAbsoluteMetricName(metricName));
      if (labelValues != null) {
        metric.labels(labelValues).set(0);
        metric.labels(labelValues).inc();
      } else {
        metric.set(0);
        metric.inc();
      }
    }
  }



  public void recordGaugeValue(String metricName, String[] labelValues,
                               double value) {
    Gauge metric =
        (Gauge)namesToCollectors.get(getAbsoluteMetricName(metricName));
    if (metric == null) {
      registerGaugeMetric(metricName, labelValues);
      metric = (Gauge)namesToCollectors.get(getAbsoluteMetricName(metricName));
    }

    if (labelValues != null) {
      metric.labels(labelValues).set(value);
    } else {
      metric.set(value);
    }
  }

  private void registerGaugeMetric(String metricName, String label, String doc) {
    String name = getAbsoluteMetricName(metricName);
    Gauge.Builder builder = Gauge.build().name(name).help(doc);
    if (label != null) {
      builder.labelNames(label);
    }
    if (doc != null) {
      builder.help(doc);
    } else {
      builder.help(metricName);
    }
    Gauge metric = builder.create();

    collectorRegistry.register(metric);
    namesToCollectors.put(name, metric);

  }

  private void registerGaugeMetric(String metricName, String[] labels) {
    registerGaugeMetric(metricName, labels, null);
    String env = System.getenv("ENV");
    if (isNotEmpty(env)) {
      env = env.replaceAll("-", "_").toLowerCase();
      registerGaugeMetric(env + "_" + metricName, labels, null);
    }
  }

  public void recordGaugeDec(String metricName, String[] labelValues) {
    Gauge metric =
        (Gauge)namesToCollectors.get(getAbsoluteMetricName(metricName));
    if (metric != null) {
      if (labelValues != null) {
        metric.labels(labelValues).dec();
      } else {
        metric.dec();
      }
    }
  }

  public void updateMetricValue(String metricName, double value) {
    String name = getAbsoluteMetricName(metricName);

    Object metric = namesToCollectors.get(name);
    if (metric != null) {
      if (metric instanceof Gauge) {
//        log.info("gauge value: "+value);
//        log.info("gauge metric name: "+metricName);
//        log.info("gauge metric : "+metric);
//        log.info("gauge log : "+metric);
        ((Gauge)metric).set(value);
      } else if (metric instanceof Meter) {
        ((Meter)metric).mark((long)value);
      } else if (metric instanceof Summary) {
        ((Summary)metric).observe(value);
      } else if (metric instanceof Histogram) {
        ((Histogram)metric).observe(value);
      }
    }
  }

  public Enumeration<MetricFamilySamples> getMetric(Set<String> metricsList) {
    return collectorRegistry.filteredMetricFamilySamples(
        getAbsoluteMetricName(metricsList));
  }

  public Enumeration<MetricFamilySamples> getMetric() {
    return collectorRegistry.filteredMetricFamilySamples(new HashSet<>());
  }

  public static String getAbsoluteMetricName(String metricName) {
    if (!metricName.startsWith(DEFAULT_METRIC_PATH_PREFIX)) {
      return DEFAULT_METRIC_PATH_PREFIX + metricName;
    }
    return metricName;
  }

  public static Set<String> getAbsoluteMetricName(Set<String> metricNames) {
    Set<String> absoluteMetricPath = new HashSet<>();
    metricNames.forEach(metricName -> {
      absoluteMetricPath.add(getAbsoluteMetricName(metricName));
    });
    return absoluteMetricPath;
  }

  @VisibleForTesting
  public Map<String, Object> getNamesToCollectors() {
    return namesToCollectors;
  }

  public void resetValues(Set<String> metrics) {
    metrics.forEach(metric -> {
      updateMetricValue(metric, 0);
    });
  }
}
