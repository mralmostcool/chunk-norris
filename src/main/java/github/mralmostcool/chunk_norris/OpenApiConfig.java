package github.mralmostcool.chunk_norris;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Chunk Norris API")
                        .version("0.0.1-SNAPSHOT")
                        .description("Vector Store & RAG API powered by Spring AI & PgVector")
                        .contact(new Contact()
                                .name("mralmostcool")
                                .url("https://github.com/mralmostcool")));
    }
}
