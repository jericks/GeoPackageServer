package org.cugos.geopackageserver

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import springfox.documentation.builders.ApiInfoBuilder
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.service.ApiInfo
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.swagger2.annotations.EnableSwagger2

@Configuration
@EnableSwagger2
class ApiConfig {

    @Bean
    Docket api() {
        new Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(RequestHandlerSelectors.basePackage("org.cugos.geopackageserver"))
                .paths(PathSelectors.any())
                .build()
                .apiInfo(apiInfo())
    }

    private ApiInfo apiInfo() {
        new ApiInfoBuilder()
                .title("GeoPackage REST API")
                .description("GeoPackage REST API")
                .version("1.0.0")
                .license("MIT")
                .licenseUrl("https://opensource.org/licenses/MIT")
                .build();
    }

}

