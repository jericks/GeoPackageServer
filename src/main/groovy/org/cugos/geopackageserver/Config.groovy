package org.cugos.geopackageserver

import geoscript.layer.GeoPackage
import geoscript.layer.Layer
import geoscript.style.DatabaseStyleRepository
import geoscript.style.StyleRepository
import geoscript.workspace.Workspace
import groovy.sql.Sql
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

@Component
class Config implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

    int port

    String hostName

    @Value('${readOnly:true}')
    boolean readOnly

    File file

    Workspace workspace

    StyleRepository styleRepository

    @Value('${file}')
    private String fileName

    @PostConstruct
    private void post() {
        file = new File(fileName).canonicalFile
        workspace = new geoscript.workspace.GeoPackage(file)
        styleRepository = DatabaseStyleRepository.forSqlite(workspace.sql)
    }

    List<String> getTileLayerNames() {
        GeoPackage.getNames(file)
    }

    List<String> getVectorLayerNames() {
       workspace.getNames()
    }

    GeoPackage getTileLayer(String name) {
        new GeoPackage(workspace.dataSource, name)
    }

    Layer getVectorLayer(String name) {
        workspace.get(name)
    }

    void deleteTileLayer(String name) {
        getTileLayer(name).delete()
    }

    List<Map<String,String>> getStyles() {
        styleRepository.getAll()
    }

    List<Map<String, String>> getStylesForLayer(String layerName) {
        styleRepository.getForLayer(layerName)
    }

    String getStyle(String layerName, String styleName) {
        styleRepository.getForLayer(layerName, styleName)
    }

    void deleteStyle(String layerName, String styleName) {
        styleRepository.delete(layerName, styleName)
    }

    void saveStyle(String layerName, String styleName, String style, Map options) {
        styleRepository.save(layerName, styleName, style, options)
    }

    @Override
    void customize(ConfigurableServletWebServerFactory factory) {
        port = factory.port
        hostName = InetAddress.localHost.hostName
    }
}
