package com.floor21.config;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sends {@code http://host:port/} to {@code /floor21/dashboard}. The Spring app uses
 * {@code server.servlet.context-path=/floor21}, so the dispatcher never sees bare {@code /}.
 */
@Configuration
public class TomcatRootRedirectConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> redirectBareRootToFloor21Dashboard() {
        return factory -> factory.addEngineValves(bareRootRedirectValve());
    }

    private static Valve bareRootRedirectValve() {
        return new ValveBase() {
            @Override
            public void invoke(Request request, Response response) throws IOException, ServletException {
                String uri = request.getRequestURI();
                if (uri == null || "/".equals(uri)) {
                    response.sendRedirect("/floor21/dashboard");
                    return;
                }
                getNext().invoke(request, response);
            }
        };
    }
}
