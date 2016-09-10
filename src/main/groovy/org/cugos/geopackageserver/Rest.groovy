package org.cugos.geopackageserver

import geoscript.feature.Feature
import geoscript.feature.Field
import geoscript.feature.Schema
import geoscript.feature.io.Readers as FeatureReaders
import geoscript.feature.io.SchemaReaders
import geoscript.filter.Filter
import geoscript.geom.Bounds
import geoscript.layer.GeoPackage
import geoscript.layer.ImageTile
import geoscript.layer.Layer
import geoscript.layer.Pyramid
import geoscript.layer.io.JsonPyramidWriter
import geoscript.layer.io.PyramidWriter
import geoscript.layer.io.Writer
import geoscript.layer.io.Writers
import geoscript.workspace.Memory
import geoscript.workspace.Workspace
import groovy.json.JsonOutput
import groovy.util.logging.Log
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.*
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.ModelAndView

import javax.imageio.ImageIO
import javax.servlet.http.HttpServletResponse
import java.awt.image.RenderedImage

@Log
@RestController
class Rest {

    @Autowired
    Config config

    @RequestMapping(value = "/", method = RequestMethod.GET, produces = "text/html")
    public ModelAndView home(Model model) {
        model.addAttribute("tiles", config.getTileLayerNames())
        model.addAttribute("layers", config.getVectorLayerNames())
        new ModelAndView("home")
    }

    @RequestMapping(value = "/tile/{name}", method = RequestMethod.GET, produces = "text/html")
    public ModelAndView tile(@PathVariable String name, Model model) {
        GeoPackage gpkg = config.getTileLayer(name)
        model.addAttribute("name", gpkg.name)
        model.addAttribute("projection", gpkg.proj.id)
        model.addAttribute("bounds", gpkg.bounds)
        model.addAttribute("minZoom", gpkg.minZoom)
        model.addAttribute("maxZoom", gpkg.maxZoom)
        model.addAttribute("pyramid", gpkg.pyramid)
        model.addAttribute("stats", gpkg.tileCounts)
        new ModelAndView("tile")
    }

    @RequestMapping(value = "/layer/{name}", method = RequestMethod.GET, produces = "text/html")
    public ModelAndView layer(@PathVariable String name, Model model) {
        Layer gpkg = config.getVectorLayer(name)
        model.addAttribute("layer", gpkg)
        new ModelAndView("layer")
    }

    @RequestMapping(value = "layers", method = RequestMethod.GET)
    @ResponseBody
    public String getLayers() {
        List<String> names = config.getVectorLayerNames()
        JsonOutput.prettyPrint(
                JsonOutput.toJson(names)
        )
    }

    @RequestMapping(value = "layers/{name}/schema", method = RequestMethod.GET)
    @ResponseBody
    public String schema(@PathVariable String name) {
        Layer layer = config.getVectorLayer(name)
        Schema schema = layer.schema
        Map json = [
                name      : schema.name,
                projection: schema.proj.id,
                geometry  : schema.geom.name,
                fields    : schema.fields.collect { Field fld ->
                    Map fldMap = [
                            name: fld.name,
                            type: fld.typ
                    ]
                    if (fld.isGeometry()) {
                        fldMap.geometry = true
                        fldMap.projection = fld.proj.id
                    }
                    fldMap
                }
        ]
        JsonOutput.prettyPrint(
                JsonOutput.toJson(json)
        )
    }

    @RequestMapping(value = "layers/{name}/count", method = RequestMethod.GET)
    @ResponseBody
    public String layerCount(@PathVariable String name) {
        Layer layer = config.getVectorLayer(name)
        JsonOutput.prettyPrint(
                JsonOutput.toJson([count: layer.count])
        )
    }

    @RequestMapping(value = "layers/{name}/bounds", method = RequestMethod.GET)
    @ResponseBody
    public String layerBounds(@PathVariable String name) {
        Layer layer = config.getVectorLayer(name)
        Bounds bounds = layer.bounds
        JsonOutput.prettyPrint(
                JsonOutput.toJson([
                        minX: bounds.minX,
                        minY: bounds.minY,
                        maxX: bounds.maxX,
                        maxY: bounds.maxY,
                        proj: bounds?.proj?.id
                ])
        )
    }

    @RequestMapping(value = "layers/{name}/features.{type}", method = RequestMethod.GET)
    @ResponseBody
    public String features(
            @PathVariable String name,
            @PathVariable String type, @RequestParam(name = "cql", required = false) String cql) {
        Layer layer = config.getVectorLayer(name)
        if (cql) {
            Workspace workspace = new Memory()
            Layer filteredLayer = workspace.create(layer.schema)
            filteredLayer.add(layer.getCursor(filter: cql))
            layer = filteredLayer
        }
        if (type.equalsIgnoreCase("json")) {
            type = "geojson"
        }
        Writer writer = Writers.find(type)
        writer.write(layer)
    }

    @RequestMapping(value = "layers/{name}", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> createLayer(@PathVariable String name, @RequestBody String schemaStr) {
        log.info("Creating Layer: ${name} with Schema: ${schemaStr}")
        if (config.readOnly) {
            new ResponseEntity<String>(HttpStatus.METHOD_NOT_ALLOWED)
        } else {
            Schema schema = SchemaReaders.find("json").read(schemaStr)
            log.info("Schema: ${schema}")
            log.info("${schema.geom} :: ${schema.proj}")
            config.getWorkspace().create(name, schema.fields)
            new ResponseEntity<String>(HttpStatus.CREATED)
        }
    }

    @RequestMapping(value = "layers/{name}", method = RequestMethod.PUT)
    @ResponseBody
    public ResponseEntity<String> updateLayer(@PathVariable String name, @RequestParam String field, @RequestParam String value, @RequestParam String cql) {
        log.info("Updating Layer: ${name} Field: ${field} to ${value} for ${cql}")
        if (config.readOnly) {
            new ResponseEntity<String>(HttpStatus.METHOD_NOT_ALLOWED)
        } else {
            Layer layer = config.getVectorLayer(name)
            Field fld = layer.schema.field(field)
            layer.update(fld, value, cql)
            new ResponseEntity<String>(HttpStatus.OK)
        }
    }

    @RequestMapping(value = "layers/{name}", method = RequestMethod.DELETE)
    @ResponseBody
    public ResponseEntity<String> deleteLayer(@PathVariable String name) {
        if (config.readOnly) {
            new ResponseEntity<String>(HttpStatus.METHOD_NOT_ALLOWED)
        } else {
            config.getWorkspace().remove(name)
            new ResponseEntity<String>(HttpStatus.OK)
        }
    }

    @RequestMapping(value = "layers/{name}/feature", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> addFeature(@PathVariable String name, @RequestBody String featureJson) {
        if (config.readOnly) {
            new ResponseEntity<String>(HttpStatus.METHOD_NOT_ALLOWED)
        } else {
            log.info("Add Feature ${featureJson} to Layer ${name}")
            Feature feature = FeatureReaders.find("geojson").read(featureJson)
            log.info("Feature = ${feature}")
            config.getVectorLayer(name).add(feature)
            new ResponseEntity<String>(HttpStatus.CREATED)
        }
    }

    @RequestMapping(value = "layers/{name}/feature/{id:.+}", method = RequestMethod.DELETE)
    @ResponseBody
    public ResponseEntity<String> deleteFeature(@PathVariable String name, @PathVariable String id) {
        log.info "Delete Feature ${id} from Layer ${name}"
        if (config.readOnly) {
            new ResponseEntity<String>(HttpStatus.METHOD_NOT_ALLOWED)
        } else {
            Filter f = Filter.id(id)
            log.info "Filter = ${f}"
            config.getVectorLayer(name).delete(f)
            new ResponseEntity<String>(HttpStatus.OK)
        }
    }

    @RequestMapping(value = "layers/{name}/feature", method = RequestMethod.DELETE)
    @ResponseBody
    public ResponseEntity<String> deleteFeatures(@PathVariable String name, @RequestParam String cql) {
        log.info "Delete Features from Layer ${name} using ${cql}"
        if (config.readOnly) {
            new ResponseEntity<String>(HttpStatus.METHOD_NOT_ALLOWED)
        } else {
            config.getVectorLayer(name).delete(cql)
            new ResponseEntity<String>(HttpStatus.OK)
        }
    }

    @RequestMapping(value = "tiles", method = RequestMethod.GET)
    @ResponseBody
    public String getTiles() {
        List<String> names = config.getTileLayerNames()
        JsonOutput.prettyPrint(
                JsonOutput.toJson(names)
        )
    }

    @RequestMapping(value = "tiles/{name}", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> createTileLayer(@PathVariable String name, @RequestBody String pyramidStr) {
        log.info "Creating Tile Layer named ${name} with Pyramid ${pyramidStr}"
        if (config.readOnly) {
            new ResponseEntity<String>(HttpStatus.METHOD_NOT_ALLOWED)
        } else {
            Pyramid pyramid = Pyramid.fromString(pyramidStr)
            log.info "Pyramid = ${pyramid}"
            GeoPackage gpkg = new GeoPackage(config.file, name, pyramid)
            new ResponseEntity<String>(HttpStatus.CREATED)
        }
    }

    @RequestMapping(value = "tiles/{name}", method = RequestMethod.DELETE)
    @ResponseBody
    public ResponseEntity<String> deleteTileLayer(@PathVariable String name) {
        log.info "Deleting Tile Layer named ${name}"
        if (config.readOnly) {
            new ResponseEntity<String>(HttpStatus.METHOD_NOT_ALLOWED)
        } else {
            config.deleteTileLayer(name)
            new ResponseEntity<String>(HttpStatus.OK)
        }
    }

    @RequestMapping(value = "tiles/{name}/tile/{z}/{x}/{y}", method = RequestMethod.GET)
    @ResponseBody
    HttpEntity<byte[]> getTile(
            @PathVariable String name,
            @PathVariable int z, @PathVariable int x, @PathVariable int y) throws IOException {
        GeoPackage gpkg = config.getTileLayer(name)
        ImageTile tile = gpkg.get(z, x, y)
        byte[] bytes = tile.data
        createHttpEntity(bytes, "png")
    }

    @RequestMapping(value = "tiles/{name}/raster/{w}/{h}/{bounds}", method = RequestMethod.GET)
    @ResponseBody
    HttpEntity<byte[]> raster(
            @PathVariable String name,
            @PathVariable int w, @PathVariable int h, @PathVariable String bounds) throws IOException {
        GeoPackage gpkg = config.getTileLayer(name)
        String type = "png"
        Bounds b = Bounds.fromString(bounds)
        b.proj = "EPSG:4326"
        RenderedImage image = gpkg.getRaster(b, w, h).image
        byte[] bytes = getBytes(image, type)
        createHttpEntity(bytes, type)
    }

    @RequestMapping(value = "tiles/{name}/pyramid", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    String pyramid(@PathVariable String name) throws IOException {
        GeoPackage gpkg = config.getTileLayer(name)
        PyramidWriter writer = new JsonPyramidWriter()
        writer.write(gpkg.pyramid)
    }

    @RequestMapping(value = "tiles/{name}/tile/counts", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    String tileCounts(@PathVariable String name) throws IOException {
        GeoPackage gpkg = config.getTileLayer(name)
        JsonOutput.prettyPrint(
                JsonOutput.toJson(gpkg.tileCounts)
        )
    }

    @RequestMapping(value = "tiles/{name}/tile/{z}/{x}/{y}", method = RequestMethod.POST)
    @ResponseBody
    HttpEntity<byte[]> createTile(
            @PathVariable String name,
            @PathVariable int z,
            @PathVariable int x, @PathVariable int y, @RequestParam MultipartFile file) throws IOException {
        if (config.readOnly) {
            new ResponseEntity<byte[]>(HttpStatus.METHOD_NOT_ALLOWED)
        } else {
            GeoPackage gpkg = config.getTileLayer(name)
            ImageTile tile = gpkg.get(z, x, y)
            tile.data = file.bytes
            gpkg.put(tile)
            byte[] bytes = tile.data
            createHttpEntity(bytes, "png")
        }
    }

    @RequestMapping(value = "tiles/{name}/tile/{z}/{x}/{y}", method = RequestMethod.DELETE)
    @ResponseBody
    HttpEntity<byte[]> deleteTile(
            @PathVariable String name,
            @PathVariable int z, @PathVariable int x, @PathVariable int y) throws IOException {
        if (config.readOnly) {
            new ResponseEntity<byte[]>(HttpStatus.METHOD_NOT_ALLOWED)
        } else {
            GeoPackage gpkg = config.getTileLayer(name)
            ImageTile tile = gpkg.get(z, x, y)
            gpkg.delete(tile)
            byte[] bytes = tile.data
            createHttpEntity(bytes, "png")
        }
    }

    @RequestMapping(value = "layers/{name}/gdal", method = RequestMethod.GET, produces = "text/xml")
    @ResponseBody
    String getLayerGdal(@PathVariable String name) throws IOException {
        Layer layer = config.getVectorLayer(name)
        """<OGRVRTDataSource>
            <OGRVRTLayer name="${name}">
                <SrcDataSource>http://${config.hostName}:${config.port}//layers/${name}/features.json</SrcDataSource>
                <LayerSRS>${layer.proj.id}</LayerSRS>
                <SrcLayer>OGRGeoJSON</SrcLayer>
            </OGRVRTLayer>
        </OGRVRTDataSource>"""
    }

    @RequestMapping(value = "tiles/{name}/gdal", method = RequestMethod.GET, produces = "text/xml")
    @ResponseBody
    String gdal(@PathVariable String name) throws IOException {
        GeoPackage gpkg = config.getTileLayer(name)
        Bounds bounds = gpkg.bounds
        """<GDAL_WMS>
     <Service name="TMS">
         <ServerUrl>http://${config.hostName}:${config.port}/tiles/${name}/tile/\${z}/\${x}/\${y}</ServerUrl>
     </Service>
     <DataWindow>
         <UpperLeftX>${bounds.minX}</UpperLeftX>
         <UpperLeftY>${bounds.maxY}</UpperLeftY>
         <LowerRightX>${bounds.maxX}</LowerRightX>
         <LowerRightY>${bounds.minY}</LowerRightY>
         <TileLevel>${gpkg.maxZoom}</TileLevel>
         <TileCountX>${gpkg.pyramid.grid(0).width}</TileCountX>
         <TileCountY>${gpkg.pyramid.grid(0).height}</TileCountY>
         <YOrigin>top</YOrigin>
     </DataWindow>
     <Projection>${gpkg.proj}</Projection>
     <BlockSizeX>${gpkg.pyramid.tileWidth}</BlockSizeX>
     <BlockSizeY>${gpkg.pyramid.tileHeight}</BlockSizeY>
     <BandsCount>3</BandsCount>
     <Cache/>
 </GDAL_WMS>
 """
    }

    @ExceptionHandler(IllegalArgumentException)
    void handleBadRequests(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.BAD_REQUEST.value())
    }

    protected HttpEntity createHttpEntity(byte[] bytes, String type) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Empty Image!")
        }
        HttpHeaders headers = new HttpHeaders()
        headers.setContentType(type.equalsIgnoreCase("png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
        headers.setContentLength(bytes.length)
        new HttpEntity<byte[]>(bytes, headers)
    }

    protected byte[] getBytes(RenderedImage image, String type) {
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        ImageIO.write(image, type, out)
        out.close()
        out.toByteArray()
    }
}
