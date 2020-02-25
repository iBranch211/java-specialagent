package io.opentracing.contrib.specialagent.adaption;

import com.lightstep.tracer.grpc.KeyValue;
import com.lightstep.tracer.grpc.Reporter;
import com.lightstep.tracer.shared.AbstractTracer;
import com.lightstep.tracer.shared.LightStepConstants;
import io.jaegertracing.internal.JaegerTracer;
import io.opentracing.Tracer;
import io.opentracing.contrib.specialagent.Logger;
import org.apache.commons.lang3.reflect.FieldUtils;

public class TracerIntrospector {
  public static final Logger logger = Logger.getLogger(TracerIntrospector.class);

  public static String getServiceName(final Tracer tracer) {
    if (tracer instanceof AbstractTracer) {
      AbstractTracer abstractTracer = (AbstractTracer) tracer;
      return getLightStepServiceName(abstractTracer);
    } else if (tracer instanceof JaegerTracer) {
      return ((JaegerTracer) tracer).getServiceName();
    }
    logger.warning("could not get service name of " + tracer.getClass().getName());
    return null;
  }

  private static String getLightStepServiceName(AbstractTracer tracer) {
    Reporter.Builder reporter;
    try {
      reporter = (Reporter.Builder) FieldUtils.readField(tracer, "reporter", true);
    } catch (IllegalAccessException e) {
      logger.warning("could not get reporter from " + tracer.getClass().getName());
      return null;
    }
    for (KeyValue keyValue : reporter.getTagsList()) {
      if (keyValue.getKey().equals(LightStepConstants.Tags.COMPONENT_NAME_KEY)) {
        return keyValue.getStringValue();
      }
    }
    logger.warning("tag not found " + LightStepConstants.Tags.COMPONENT_NAME_KEY);
    return null;
  }
}