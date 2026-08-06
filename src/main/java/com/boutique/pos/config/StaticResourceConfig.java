package com.boutique.pos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

// Sirve los archivos subidos (logos de tienda) como recursos estáticos públicos.
// Dev: carpeta local dentro del proyecto backend. Prod: la misma ruta relativa,
// pero apuntando al disco del servidor — solo cambia app.uploads.dir.
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Paths.get(uploadsDir).toAbsolutePath() + "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
