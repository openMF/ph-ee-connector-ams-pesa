package org.mifos.connector.ams.pesacore.camel.route;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class PesaRouteBuilder extends RouteBuilder {

    @Override
    public void configure() {

        from("direct:transfer-validation")
                .id("transfer-validation")
                .log(LoggingLevel.INFO, "## Transfer Validation route")
                .process(e -> {
                    // Do stuff here
                })
                .setBody(constant(null));

        from("direct:transfer-settlement")
                .id("transfer-settlement")
                .log(LoggingLevel.INFO, "## Transfer Settlement route")
                .process(e -> {
                    // Do stuff here
                })
                .setBody(constant(null));

    }
}
