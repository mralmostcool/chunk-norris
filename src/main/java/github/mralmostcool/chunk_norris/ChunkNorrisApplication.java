package github.mralmostcool.chunk_norris;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ChunkNorrisApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChunkNorrisApplication.class, args);
	}

}
