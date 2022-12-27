package controller;

import model.DatabaseConnectionPool;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.ResourceUtils;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.time.*;
import java.time.format.*;

@SpringBootApplication
public class RestServiceApplication {
    public static void main(String[] args) throws SQLException, IOException {
        Properties configProps = new Properties();
        configProps.load(new FileInputStream(ResourceUtils.getFile("classpath:properties/api.properties")));
        String API_VERSION = configProps.getProperty("API_VERSION");

        SpringApplication.run(RestServiceApplication.class, args);

        System.out.println();
        System.out.println("                                        /$$                                       ");
        System.out.println("                                       |__/                                       ");
        System.out.println("       /$$    /$$   /$$    /$$$$$$$     /$$     /$$$$$$      /$$$$$$      /$$$$$$ ");
        System.out.println("      |__/   | $$  | $$   | $$__  $$   | $$    /$$__  $$    /$$__  $$    /$$__  $$");
        System.out.println("       /$$   | $$  | $$   | $$  \\ $$   | $$   | $$  \\ $$   | $$$$$$$$   | $$  \\__/");
        System.out.println("      | $$   | $$  | $$   | $$  | $$   | $$   | $$  | $$   | $$_____/  |  $$      ");
        System.out.println("      | $$   |  $$$$$$/   | $$  | $$   | $$   | $$$$$$$/   |  $$$$$$$  |  $$      ");
        System.out.println("      | $$    \\______/    |__/  |__/   |__/   | $$____/     \\_______/   |__/      ");
        System.out.println(" /$$  | $$                                    | $$                                ");
        System.out.println("|  $$$$$$/                                    | $$                                ");
        System.out.println(" \\______/=====================================|__/================================");
        System.out.println(" \u001B[32m:: juniper ::\u001B[0m                    (v" + API_VERSION + ")");
        System.out.println();
        System.out.println(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").format(LocalDateTime.now()) + "  \u001B[32mINFO \u001B[35mGOOD\u001B[35m \u001B[0m--- [           main] \u001B[36mmodel.DatabaseConnectionPool\u001B[0m             : Initialized DatabaseConnectionPool with " + DatabaseConnectionPool.size() + " connections");
    }
}