package cn.edu.bjfu.redislearn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import springfox.documentation.oas.annotations.EnableOpenApi;

/**
 * @author chaos
 */
@EnableOpenApi
@SpringBootApplication
public class RedisLearnApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisLearnApplication.class, args);
    }

}
