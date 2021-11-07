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
import geoscript.layer.PbfVectorTileRenderer
import geoscript.layer.Pyramid
import geoscript.layer.Tile
import geoscript.layer.VectorTileRenderer
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
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import springfox.documentation.annotations.ApiIgnore

import javax.imageio.ImageIO
import javax.servlet.http.HttpServletResponse
import java.awt.image.RenderedImage

@Log
@Api(value = "GeoPackage REST API")
@RestController
class Rest {

    @Autowired
    Config config

    @RequestMapping(value = "/", method = RequestMethod.GET, produces = "text/html")
    @ApiIgnore
    ModelAndView home(Model model) {
        model.addAttribute("tiles", config.getTileLayerNames())
        model.addAttribute("layers", config.getVectorLayerNames())
        model.addAttribute("styles", config.getStyles())
        new ModelAndView("home")
    }

    @RequestMapping(value = "/tile/{name}", method = RequestMethod.GET, produces = "text/html")
    @ApiIgnore
    ModelAndView tile(@PathVariable String name, Model model) {
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
    @ApiIgnore
    ModelAndView layer(@PathVariable String name, Model model) {
        Layer gpkg = config.getVectorLayer(name)
        model.addAttribute("layer", gpkg)
        new ModelAndView("layer")
    }

    @RequestMapping(value = "layers", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(value="Vector Layers", notes="Get a List of Vector Layer names")
    String getLayers() {
        List<String> names = config.getVectorLayerNames()
        JsonOutput.prettyPrint(
                JsonOutput.toJson(names)
        )
    }

    @RequestMapping(value = "layers/{name}/schema", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(value="Schema", notes="Get a Vector Layer Schema")
    String schema(
            @PathVariable @ApiParam("Layer Name") String name
    ) {
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
    @ApiOperation(value="Feature Count", notes="Get a Vector Layer feature count")
    String layerCount(
            @PathVariable @ApiParam("Layer Name") String name
    ) {
        Layer layer = config.getVectorLayer(name)
        JsonOutput.prettyPrint(
                JsonOutput.toJson([count: layer.count])
        )
    }

    @RequestMapping(value = "layers/{name}/bounds", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(value="Layer Bounds", notes="Get the Bounds of a Vector Layer")
    String layerBounds(
            @PathVariable @ApiParam("Layer Name") String name
    ) {
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
    @ApiOperation(value="Features", notes="Get Vector Layer Features")
    String features(
            @PathVariable @ApiParam("Layer Name") String name,
            @PathVariable @ApiParam("Output Type") String type,
            @RequestParam(name = "cql", required = false) @ApiParam("CQL Filter") String cql
    ) {
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
    @ApiOperation(value="Create Layer", notes="Create a new Vector Layer")
    ResponseEntity<String> createLayer(
            @PathVariable @ApiParam("Layer Name") String name,
            @RequestBody @ApiParam("Schema Definition") String schemaStr
    ) {
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
    @ApiOperation(value="Update Layer", notes="Update Features in a Vector Layer")
    ResponseEntity<String> updateLayer(
            @PathVariable @ApiParam("Layer Name") String name,
            @RequestParam @ApiParam("Field Name") String field,
            @RequestParam @ApiParam("Value") String value,
            @RequestParam @ApiParam("CQL Filter") String cql
    ) {
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
    @ApiOperation(value="Delete Layer", notes="Delete a Vector Layer")
    ResponseEntity<String> deleteLayer(
            @PathVariable @ApiParam("Layer Name") String name
    ) {
        if (config.readOnly) {
            new ResponseEntity<String>(HttpStatus.METHOD_NOT_ALLOWED)
        } else {
            config.getWorkspace().remove(name)
            new ResponseEntity<String>(HttpStatus.OK)
        }
    }

    @RequestMapping(value = "layers/{name}/feature", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(value="Add Feature", notes="Add a Feature to a Vector Layer")
    ResponseEntity<String> addFeature(
            @PathVariable @ApiParam("Layer Name") String name,
            @RequestBody @ApiParam("Feature GeoJSON") String featureJson
    ) {
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
    @ApiOperation(value="Delete Feature", notes="Delete a Feature from a Vector Layer")
    ResponseEntity<String> deleteFeature(
            @PathVariable @ApiParam("Layer Name") String name,
            @PathVariable @ApiParam("Feature ID") String id
    ) {
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
    @ApiOperation(value="Delete Features", notes="Delete Features from a Vector Layer")
    ResponseEntity<String> deleteFeatures(
            @PathVariable @ApiParam("Layer Name") String name,
            @RequestParam @ApiParam("CQL Filter") String cql
    ) {
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
    @ApiOperation(value="Tile Names", notes="Get Tile Layer Names")
    String getTiles() {
        List<String> names = config.getTileLayerNames()
        JsonOutput.prettyPrint(
                JsonOutput.toJson(names)
        )
    }

    @RequestMapping(value = "tiles/{name}", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(value="Create Tile Layer", notes="Create a new Tile Layer")
    ResponseEntity<String> createTileLayer(
            @PathVariable @ApiParam("Tile Name") String name,
            @RequestBody @ApiParam("Pyramid Definition") String pyramidStr
    ) {
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
    @ApiOperation(value="Delete Tile Layer", notes="Delete a Tile Layer")
    ResponseEntity<String> deleteTileLayer(
            @PathVariable @ApiParam("Tile Name") String name
    ) {
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
    @ApiOperation(value="Get a Tile", notes="Get a Tile")
    HttpEntity<byte[]> getTile(
            @PathVariable @ApiParam("Tile Name") String name,
            @PathVariable @ApiParam("Zoom Level") int z,
            @PathVariable @ApiParam("X") int x,
            @PathVariable @ApiParam("Y") int y
    ) throws IOException {
        GeoPackage gpkg = config.getTileLayer(name)
        ImageTile tile = gpkg.get(z, x, y)
        byte[] bytes = tile.data
        createHttpEntity(bytes, "png")
    }

    @RequestMapping(value = "tiles/{name}/raster/{w}/{h}/{bounds}", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(value="Get a Raster", notes="Stitch together a Raster from a set of tiles")
    HttpEntity<byte[]> raster(
            @PathVariable @ApiParam(value = "Layer Name") String name,
            @PathVariable @ApiParam(value = "Width") int w,
            @PathVariable @ApiParam(value = "Height") int h,
            @PathVariable @ApiParam(value = "Bounds") String bounds
    ) throws IOException {
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
    @ApiOperation(value="Get Pyramid", notes="Get a Pyramid from a Tile Layer")
    String pyramid(
            @PathVariable @ApiParam("Tile Name") String name
    ) throws IOException {
        GeoPackage gpkg = config.getTileLayer(name)
        PyramidWriter writer = new JsonPyramidWriter()
        writer.write(gpkg.pyramid)
    }

    @RequestMapping(value = "tiles/{name}/tile/counts", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    @ApiOperation(value="Get Tile Layer Counts", notes="Get Tile Layer Counts")
    String tileCounts(
            @PathVariable @ApiParam("Tile Name") String name
    ) throws IOException {
        GeoPackage gpkg = config.getTileLayer(name)
        JsonOutput.prettyPrint(
                JsonOutput.toJson(gpkg.tileCounts)
        )
    }

    @RequestMapping(value = "tiles/{name}/tile/{z}/{x}/{y}", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(value="Create a Tile", notes="Create a Tile")
    HttpEntity<byte[]> createTile(
            @PathVariable @ApiParam("Tile Name") String name,
            @PathVariable @ApiParam("Zoom Level") int z,
            @PathVariable @ApiParam("X") int x,
            @PathVariable @ApiParam("Y") int y,
            @RequestParam @ApiParam("Tile File") MultipartFile file
    ) throws IOException {
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
    @ApiOperation(value="Delete a Tile", notes="Delete a Tile")
    HttpEntity<byte[]> deleteTile(
            @PathVariable @ApiParam("Tile Name") String name,
            @PathVariable @ApiParam("Zoom Level") int z,
            @PathVariable @ApiParam("X") int x,
            @PathVariable @ApiParam("Y") int y
    ) throws IOException {
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
    @ApiOperation(value="Get Vector Layer GDAL Layer", notes="Get Vector Layer GDAL Layer")
    String getLayerGdal(
            @PathVariable @ApiParam("Layer Name") String name
    ) throws IOException {
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
    @ApiOperation(value="Get Tile Layer GDAL Layer", notes="Get Tile Layer GDAL Layer")
    String gdal(
            @PathVariable @ApiParam("Layer Name") String name
    ) throws IOException {
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

    @RequestMapping(value = "style", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    @ApiOperation(value="Get all styles", notes="Get all styles")
    String getAllStyles() throws IOException {
        List<Map<String,String>> styles = config.getStyles()
        JsonOutput.prettyPrint(
            JsonOutput.toJson(styles)
        )
    }

    @RequestMapping(value = "style/{layerName}", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    @ApiOperation(value="Get all styles for a Layer", notes="Get all styles for a Layer")
    String getAllStylesForLayer(
            @PathVariable @ApiParam("Layer Name") String layerName
    ) throws IOException {
        List<Map<String,String>> styles = config.getStylesForLayer(layerName)
        JsonOutput.prettyPrint(
                JsonOutput.toJson(styles)
        )
    }

    @RequestMapping(value = "style/{layerName}/{styleName}", method = RequestMethod.POST, produces = "text/xml")
    @ResponseBody
    @ApiOperation(value="Save a style", notes="Save a style")
    ResponseEntity<String> saveStyle(
            @PathVariable @ApiParam("Layer Name") String layerName,
            @PathVariable @ApiParam("Style Name") String styleName,
            @RequestBody @ApiParam("Style") MultipartFile file
    ) throws IOException {
        if (config.readOnly) {
            new ResponseEntity<String>(HttpStatus.METHOD_NOT_ALLOWED)
        } else {
            config.saveStyle(layerName, styleName, new String(file.bytes), [:])
            new ResponseEntity<String>(HttpStatus.OK)
        }
    }

    @RequestMapping(value = "style/{layerName}/{styleName}", method = RequestMethod.DELETE, produces = "text/xml")
    @ResponseBody
    @ApiOperation(value="Delete a style", notes="Delete a style")
    ResponseEntity<String> deleteStyle(
            @PathVariable @ApiParam("Layer Name") String layerName,
            @PathVariable @ApiParam("Style Name") String styleName
    ) throws IOException {
        if (config.readOnly) {
            new ResponseEntity<String>(HttpStatus.METHOD_NOT_ALLOWED)
        } else {
            config.deleteStyle(layerName, styleName)
            new ResponseEntity<String>(HttpStatus.OK)
        }
    }

    @RequestMapping(value = "style/{layerName}/{styleName}", method = RequestMethod.GET, produces = "text/xml")
    @ResponseBody
    @ApiOperation(value="Get all styles for a Layer", notes="Get all styles for a Layer")
    String getStyle(
            @PathVariable @ApiParam("Layer Name") String layerName,
            @PathVariable @ApiParam("Style Name") String styleName
    ) throws IOException {
        config.getStyle(layerName, styleName)
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

    @RequestMapping(value = "layers/{name}/tile/{type}/{z}/{x}/{y}", method = RequestMethod.GET)
    @ResponseBody
    HttpEntity<byte[]> vectorTiles(@PathVariable String name, @PathVariable String type, @PathVariable int z, @PathVariable int x, @PathVariable int y) {
        Layer layer = config.getVectorLayer(name)
        Pyramid pyramid = Pyramid.createGlobalMercatorPyramid()
        pyramid.origin = Pyramid.Origin.TOP_LEFT
        Bounds bounds = pyramid.bounds(new Tile(z,x,y))
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM
        byte[] bytes
        if (type.equalsIgnoreCase("pbf")) {
            PbfVectorTileRenderer renderer = new PbfVectorTileRenderer(layer, layer.schema.fields)
            bytes = renderer.render(bounds)
        } else {
            if (type.equalsIgnoreCase("json")) {
                type = "geojson"
                mediaType = MediaType.APPLICATION_JSON
            }
            Writer writer = Writers.find(type)
            VectorTileRenderer renderer = new VectorTileRenderer(writer, layer, layer.schema.fields)
            bytes = renderer.render(bounds)
        }
        HttpHeaders headers = new HttpHeaders()
        headers.setContentType(mediaType)
        headers.setContentLength(bytes.length)
        new HttpEntity<byte[]>(bytes, headers)
    }

}
