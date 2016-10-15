package org.cugos.geopackageserver

import geoscript.layer.GeoPackage
import geoscript.layer.Layer
import geoscript.workspace.Workspace
import groovy.sql.Sql
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

@Component
class Config implements ApplicationListener<EmbeddedServletContainerInitializedEvent> {

    int port

    String hostName

    @Value('${readOnly:true}')
    boolean readOnly

    File file

    Workspace workspace

    @Value('${file}')
    private String fileName

    @PostConstruct
    private void post() {
        file = new File(fileName).canonicalFile
        workspace = new geoscript.workspace.GeoPackage(file)
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

    public void onApplicationEvent(EmbeddedServletContainerInitializedEvent event) {
        port = event.getEmbeddedServletContainer().getPort()
        hostName = InetAddress.localHost.hostName
    }
}
