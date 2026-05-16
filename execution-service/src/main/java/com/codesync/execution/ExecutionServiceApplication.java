package com.codesync.execution;

import com.codesync.execution.entity.SupportedLanguage;
import com.codesync.execution.repository.SupportedLanguageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;

import java.util.List;

@Slf4j
@SpringBootApplication
@EnableDiscoveryClient
@RequiredArgsConstructor
@EnableRabbit
public class ExecutionServiceApplication {

    public static void main(String[] args) {

        SpringApplication.run(ExecutionServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner seedLanguages(
            SupportedLanguageRepository repo) {
        return args -> {
            if (repo.count() > 0) {
                log.info("Languages already seeded. Skipping.");
                return;
            }

            log.info("Seeding supported languages...");

            List<SupportedLanguage> languages = List.of(

                    SupportedLanguage.builder()
                            .name("java")
                            .displayName("Java")
                            .version("21")
                            .dockerImage("eclipse-temurin:21-jdk")
                            .fileExtension(".java")
                            .helloWorldCode("""
                                public class Main {
                                    public static void main(String[] args) {
                                        System.out.println("Hello, World!");
                                    }
                                }
                                """)
                            .isActive(true)
                            .build(),

                    SupportedLanguage.builder()
                            .name("python")
                            .displayName("Python 3")
                            .version("3.11")
                            .dockerImage("python:3.11-slim")
                            .fileExtension(".py")
                            .helloWorldCode(
                                    "print(\"Hello, World!\")")
                            .isActive(true)
                            .build(),

                    SupportedLanguage.builder()
                            .name("javascript")
                            .displayName("JavaScript (Node.js)")
                            .version("18")
                            .dockerImage("node:18-slim")
                            .fileExtension(".js")
                            .helloWorldCode(
                                    "console.log(\"Hello, World!\");")
                            .isActive(true)
                            .build(),

                    SupportedLanguage.builder()
                            .name("c")
                            .displayName("C")
                            .version("GCC 13")
                            .dockerImage("gcc:13")
                            .fileExtension(".c")
                            .helloWorldCode("""
                                #include <stdio.h>
                                int main() {
                                    printf("Hello, World!");
                                    return 0;
                                }
                                """)
                            .isActive(true)
                            .build(),

                    SupportedLanguage.builder()
                            .name("cpp")
                            .displayName("C++")
                            .version("GCC 13")
                            .dockerImage("gcc:13")
                            .fileExtension(".cpp")
                            .helloWorldCode("""
                                #include <iostream>
                                int main() {
                                    std::cout << "Hello, World!" << std::endl;
                                    return 0;
                                }
                                """)
                            .isActive(true)
                            .build(),

                    SupportedLanguage.builder()
                            .name("go")
                            .displayName("Go")
                            .version("1.21")
                            .dockerImage("golang:1.21-alpine")
                            .fileExtension(".go")
                            .helloWorldCode("""
                                package main
                                import "fmt"
                
                                func main() {
                                    fmt.Println("Hello, World!")
                                }
                                """)
                            .isActive(true)
                            .build(),

                    SupportedLanguage.builder()
                            .name("rust")
                            .displayName("Rust")
                            .version("1.75")
                            .dockerImage("rust:1.75-slim")
                            .fileExtension(".rs")
                            .helloWorldCode("""
                                fn main() {
                                    println!("Hello, World!");
                                }
                                """)
                            .isActive(true)
                            .build(),

                    SupportedLanguage.builder()
                            .name("typescript")
                            .displayName("TypeScript")
                            .version("5.0")
                            .dockerImage("node:18")
                            .fileExtension(".ts")
                            .helloWorldCode(
                                    "console.log(\"Hello, World!\");")
                            .isActive(true)
                            .build(),

                    SupportedLanguage.builder()
                            .name("kotlin")
                            .displayName("Kotlin")
                            .version("1.9")
                            .dockerImage("gradle:jdk21")
                            .fileExtension(".kt")
                            .helloWorldCode("""
                                fun main() {
                                    println("Hello, World!")
                                }
                                """)
                            .isActive(true)
                            .build(),

                    SupportedLanguage.builder()
                            .name("ruby")
                            .displayName("Ruby")
                            .version("3.2")
                            .dockerImage("ruby:3.2-slim")
                            .fileExtension(".rb")
                            .helloWorldCode(
                                    "puts \"Hello, World!\"")
                            .isActive(true)
                            .build(),

                    SupportedLanguage.builder()
                            .name("php")
                            .displayName("PHP")
                            .version("8.2")
                            .dockerImage("php:8.2-cli")
                            .fileExtension(".php")
                            .helloWorldCode("""
                                <?php
                                echo "Hello, World!";
                                """)
                            .isActive(true)
                            .build(),

                    SupportedLanguage.builder()
                            .name("swift")
                            .displayName("Swift")
                            .version("5.9")
                            .dockerImage("swift:5.9")
                            .fileExtension(".swift")
                            .helloWorldCode(
                                    "print(\"Hello, World!\")")
                            .isActive(true)
                            .build()
            );

            repo.saveAll(languages);
            log.info("Seeded {} languages.", languages.size());
        };
    }

}
