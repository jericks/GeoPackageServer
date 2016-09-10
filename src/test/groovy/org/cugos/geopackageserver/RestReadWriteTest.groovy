package org.cugos.geopackageserver

import com.jayway.jsonpath.JsonPath
import groovy.util.logging.Log
import org.apache.commons.io.FileUtils
import org.junit.*
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.SpringApplicationConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.web.context.WebApplicationContext

import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup

@Log
@RunWith(SpringJUnit4ClassRunner)
@SpringApplicationConfiguration(classes = App)
@TestPropertySource(locations = "classpath:readWrite.properties")
@WebAppConfiguration
class RestReadWriteTest {

    private MockMvc mockMvc

    @BeforeClass
    static void preSetup() {
        File source = new File("src/test/resources/data.gpkg")
        File destination = new File("src/test/resources/data2.gpkg")
        FileUtils.copyFile(source, destination)
    }

    @AfterClass
    static void tearDown() {
        File file = new File("src/test/resources/data2.gpkg")
        file.delete()
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Before
    void setup() {
        mockMvc = webAppContextSetup(webApplicationContext).build()
    }

    @Test
    void homePage() {
        mockMvc.perform(get("/")).andExpect(status().isOk())
    }

    @Test
    void tilePage() {
        mockMvc.perform(get("/tile/world")).andExpect(status().isOk())
    }

    @Test
    void tiles() {
        mockMvc.perform(get("/tiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$', hasSize(2)))
                .andExpect(jsonPath('$[0]', is('world')))
                .andExpect(jsonPath('$[1]', is('world_mercator')))
    }

    @Test
    void layerPage() {
        mockMvc.perform(get("/layer/countries")).andExpect(status().isOk())
    }

    @Test
    void layers() {
        mockMvc.perform(get("/layers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$', hasSize(4)))
                .andExpect(jsonPath('$[0]', is('countries')))
                .andExpect(jsonPath('$[1]', is('ocean')))
                .andExpect(jsonPath('$[2]', is('places')))
                .andExpect(jsonPath('$[3]', is('states')))
    }

    @Test
    void schema() {
        mockMvc.perform(get("/layers/countries/schema"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.name', is('countries')))
                .andExpect(jsonPath('$.projection', is('EPSG:4326')))
                .andExpect(jsonPath('$.geometry', is('the_geom')))
                .andExpect(jsonPath('$.fields', hasSize(47)))
                .andExpect(jsonPath('$.fields[0].name', is('the_geom')))
                .andExpect(jsonPath('$.fields[0].type', is('MultiPolygon')))
                .andExpect(jsonPath('$.fields[0].geometry', is(true)))
                .andExpect(jsonPath('$.fields[0].projection', is('EPSG:4326')))
    }

    @Test
    void layerCount() {
        mockMvc.perform(get("/layers/countries/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.count', is(177)))
    }

    @Test
    void layerBounds() {
        mockMvc.perform(get("/layers/countries/bounds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.minX', closeTo(new BigDecimal(-179.99), new BigDecimal(0.01))))
                .andExpect(jsonPath('$.minY', is(-90.00000000000003 as Double)))
                .andExpect(jsonPath('$.maxX', is(180.00000000000014 as Double)))
                .andExpect(jsonPath('$.maxY', is(83.64513000000002 as Double)))
                .andExpect(jsonPath('$.proj', is("EPSG:4326")))
    }

    @Test
    void layerFeatures() {
        mockMvc.perform(get("/layers/countries/features.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.type', is('FeatureCollection')))
                .andExpect(jsonPath('$.features', hasSize(177)))
    }

    @Test
    void layerCreateAndDelete() {
        String schema = '{"name":"points","projection": "EPSG:4326","geometry": "geom","fields":[{"name":"geom","type":"Point","geometry": "true", "projection":"EPSG:4326"},{"name":"pid","type":"int"}]}'
        mockMvc.perform(post("/layers/points").content(schema))
                .andExpect(status().isCreated())
        mockMvc.perform(delete("/layers/points"))
                .andExpect(status().isOk())
    }


    @Test
    void addFeature() {

        // Create Layer
        String schema = '{"name":"points","projection": "EPSG:4326","geometry": "geom","fields":[{"name":"geom","type":"Point","geometry": "true", "projection":"EPSG:4326"},{"name":"pid","type":"int"}]}'
        mockMvc.perform(post("/layers/points").content(schema))
                .andExpect(status().isCreated())

        // Add Feature
        String feature = '{"type":"Feature","geometry":{"type":"Point","coordinates":[1,2]},"properties":{"pid":1}}'
        mockMvc.perform(post("/layers/points/feature").content(feature))
                .andExpect(status().isCreated())

        // Get Features
        MvcResult result = mockMvc.perform(get("/layers/points/features.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.type', is('FeatureCollection')))
                .andExpect(jsonPath('$.features', hasSize(1)))
                .andReturn()

        log.info("Response = " + result.response.contentAsString)
        String fid = JsonPath.read(result.response.contentAsString, '$.features[0].id')
        log.info("FID = ${fid}")

        // Delete Feature
        mockMvc.perform(delete("/layers/points/feature/${fid}"))
            .andExpect(status().isOk())

        // Get Features again
        mockMvc.perform(get("/layers/points/features.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.type', is('FeatureCollection')))
                .andExpect(jsonPath('$.features', hasSize(0)))

        // Delete Layer
        mockMvc.perform(delete("/layers/points"))
                .andExpect(status().isOk())
    }

    @Test
    void deleteFeatureWithCql() {

        // Create Layer
        String schema = '{"name":"points","projection": "EPSG:4326","geometry": "geom","fields":[{"name":"geom","type":"Point","geometry": "true", "projection":"EPSG:4326"},{"name":"pid","type":"int"}]}'
        mockMvc.perform(post("/layers/points").content(schema))
                .andExpect(status().isCreated())

        // Add Feature
        [
            '{"type":"Feature","geometry":{"type":"Point","coordinates":[1,2]},"properties":{"pid":1}}',
            '{"type":"Feature","geometry":{"type":"Point","coordinates":[1,2]},"properties":{"pid":2}}',
            '{"type":"Feature","geometry":{"type":"Point","coordinates":[1,2]},"properties":{"pid":3}}',
            '{"type":"Feature","geometry":{"type":"Point","coordinates":[1,2]},"properties":{"pid":4}}'
        ].each { String feature ->
            mockMvc.perform(post("/layers/points/feature").content(feature))
                    .andExpect(status().isCreated())
        }

        // Get Features
        mockMvc.perform(get("/layers/points/features.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.type', is('FeatureCollection')))
                .andExpect(jsonPath('$.features', hasSize(4)))
                .andReturn()

        // Delete Features
        mockMvc.perform(delete("/layers/points/feature?cql=pid>2"))
                .andExpect(status().isOk())

        // Get Features again
        mockMvc.perform(get("/layers/points/features.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.type', is('FeatureCollection')))
                .andExpect(jsonPath('$.features', hasSize(2)))

        // Delete Layer
        mockMvc.perform(delete("/layers/points"))
                .andExpect(status().isOk())
    }

    @Test
    void updateFeatures() {

        // Create Layer
        String schema = '{"name":"points","projection": "EPSG:4326","geometry": "geom","fields":[{"name":"geom","type":"Point","geometry": "true", "projection":"EPSG:4326"},{"name":"pid","type":"int"},{"name":"area","type":"double"}]}'
        mockMvc.perform(post("/layers/points").content(schema))
                .andExpect(status().isCreated())

        // Add Feature
        [
                '{"type":"Feature","geometry":{"type":"Point","coordinates":[1,2]},"properties":{"pid":1}}',
                '{"type":"Feature","geometry":{"type":"Point","coordinates":[1,2]},"properties":{"pid":2}}',
                '{"type":"Feature","geometry":{"type":"Point","coordinates":[1,2]},"properties":{"pid":3}}',
                '{"type":"Feature","geometry":{"type":"Point","coordinates":[1,2]},"properties":{"pid":4}}'
        ].each { String feature ->
            mockMvc.perform(post("/layers/points/feature").content(feature))
                    .andExpect(status().isCreated())
        }

        // Get Features
        mockMvc.perform(get("/layers/points/features.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.type', is('FeatureCollection')))
                .andExpect(jsonPath('$.features', hasSize(4)))

        // Update Features
        mockMvc.perform(put("/layers/points").param("field","area").param("value","10").param("cql","pid>2"))
                .andExpect(status().isOk())

        // Get Features again
        mockMvc.perform(get("/layers/points/features.json").param("cql","area=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.type', is('FeatureCollection')))
                .andExpect(jsonPath('$.features', hasSize(2)))

        // Delete Layer
        mockMvc.perform(delete("/layers/points"))
                .andExpect(status().isOk())
    }

    @Test
    void createAndDeleteTileLayer() {

        // Create Tile Layer
        String pyramid = 'GlobalMercator'
        mockMvc.perform(post("/tiles/mercator").content(pyramid))
                .andExpect(status().isCreated())

        // Add a Tile
        byte[] bytes = getClass().getClassLoader().getResource("tile.png").bytes
        mockMvc.perform(fileUpload("/tiles/mercator/tile/0/0/0").file("file", bytes))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))

        // Delete Tile Layer
        mockMvc.perform(delete("/tiles/mercator"))
                .andExpect(status().isOk())

    }

    @Test
    void tileRaster() {
        MvcResult result = mockMvc.perform(get("/tiles/world/raster/400/400/-156.533203,3.688855,-50.712891,56.800878"))
                .andExpect(status().isOk()).andReturn()
        assertTrue result.response.contentAsByteArray.length > 0
    }

    @Test
    void tile() {
        MvcResult result = mockMvc.perform(get("/tiles/world/tile/0/0/0"))
                .andExpect(status().isOk()).andReturn()
        assertTrue result.response.contentAsByteArray.length > 0
    }

    @Test
    void createTile() {
       byte[] bytes = getClass().getClassLoader().getResource("tile.png").bytes
       mockMvc.perform(fileUpload("/tiles/world/tile/6/0/0").file("file", bytes))
               .andExpect(status().isOk())
               .andExpect(content().bytes(bytes))
    }

    @Test
    void deleteTile() {
        byte[] bytes = getClass().getClassLoader().getResource("tile.png").bytes
        mockMvc.perform(fileUpload("/tiles/world/tile/6/0/0").file("file", bytes))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
        mockMvc.perform(delete("/tiles/world/tile/6/0/0"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
        mockMvc.perform(get("/tiles/world/tile/6/0/0")).andExpect(status().is4xxClientError())
    }

    @Test
    void tileCounts() {
        mockMvc.perform(get("/tiles/world/tile/counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$', hasSize(4)))
                .andExpect(jsonPath('$[0].zoom', is(0)))
                .andExpect(jsonPath('$[0].tiles', is(2)))
                .andExpect(jsonPath('$[0].total', is(2)))
                .andExpect(jsonPath('$[0].percent', is(1.0 as Double)))
                .andExpect(jsonPath('$[1].zoom', is(1)))
                .andExpect(jsonPath('$[1].tiles', is(8)))
                .andExpect(jsonPath('$[1].total', is(8)))
                .andExpect(jsonPath('$[1].percent', is(1.0 as Double)))
                .andExpect(jsonPath('$[2].zoom', is(2)))
                .andExpect(jsonPath('$[2].tiles', is(32)))
                .andExpect(jsonPath('$[2].total', is(32)))
                .andExpect(jsonPath('$[2].percent', is(1.0 as Double)))
                .andExpect(jsonPath('$[3].zoom', is(3)))
                .andExpect(jsonPath('$[3].tiles', is(128)))
                .andExpect(jsonPath('$[3].total', is(128)))
                .andExpect(jsonPath('$[3].percent', is(1.0 as Double)))
    }

    @Test
    void pyramid() {
        mockMvc.perform(get("/tiles/world/pyramid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.proj', is('EPSG:4326')))
                .andExpect(jsonPath('$.bounds.minX', is(-179.99 as Double)))
                .andExpect(jsonPath('$.bounds.minY', is(-89.99 as Double)))
                .andExpect(jsonPath('$.bounds.maxX', is(179.99 as Double)))
                .andExpect(jsonPath('$.bounds.maxY', is(89.99 as Double)))
                .andExpect(jsonPath('$.origin', is('TOP_LEFT')))
                .andExpect(jsonPath('$.tileSize.width', is(256)))
                .andExpect(jsonPath('$.tileSize.height', is(256)))
                .andExpect(jsonPath('$.grids', hasSize(20)))
                .andExpect(jsonPath('$.grids[0].z', is(0)))
                .andExpect(jsonPath('$.grids[0].width', is(2)))
                .andExpect(jsonPath('$.grids[0].height', is(1)))
                .andExpect(jsonPath('$.grids[0].xres', is(0.703125 as Double)))
                .andExpect(jsonPath('$.grids[0].yres', is(0.703125 as Double)))
                .andExpect(jsonPath('$.grids[19].z', is(19)))
                .andExpect(jsonPath('$.grids[19].width', is(1048576)))
                .andExpect(jsonPath('$.grids[19].height', is(524288)))
                .andExpect(jsonPath('$.grids[19].xres', closeTo(1.341104507446289E-6 as Double, 0.001)))
                .andExpect(jsonPath('$.grids[19].yres', closeTo(1.341104507446289E-6 as Double, 0.001)))
    }

    @Test
    void gdal() {
        mockMvc.perform(get("/tiles/world/gdal"))
                .andExpect(status().isOk())
                .andExpect(xpath("/GDAL_WMS/Service/@name").string("TMS"))
                .andExpect(xpath("/GDAL_WMS/Service/ServerUrl").string(startsWith('http://')))
                .andExpect(xpath("/GDAL_WMS/Service/ServerUrl").string(containsString('tiles/world/tile/${z}/${x}/${y}')))
                .andExpect(xpath("/GDAL_WMS/DataWindow/UpperLeftX").number(-179.99))
                .andExpect(xpath("/GDAL_WMS/DataWindow/UpperLeftY").number(89.99))
                .andExpect(xpath("/GDAL_WMS/DataWindow/LowerRightX").number(179.99))
                .andExpect(xpath("/GDAL_WMS/DataWindow/LowerRightY").number(-89.99))
                .andExpect(xpath("/GDAL_WMS/DataWindow/TileLevel").number(3))
                .andExpect(xpath("/GDAL_WMS/DataWindow/TileCountX").number(2))
                .andExpect(xpath("/GDAL_WMS/DataWindow/TileCountY").number(1))
                .andExpect(xpath("/GDAL_WMS/DataWindow/YOrigin").string('top'))
                .andExpect(xpath("/GDAL_WMS/Projection").string('EPSG:4326'))
                .andExpect(xpath("/GDAL_WMS/BlockSizeX").string('256'))
                .andExpect(xpath("/GDAL_WMS/BlockSizeY").string('256'))
                .andExpect(xpath("/GDAL_WMS/BandsCount").string('3'))
    }

    @Test
    void layerGdal() {
        mockMvc.perform(get("/layers/countries/gdal"))
                .andExpect(status().isOk())
                .andExpect(xpath("/OGRVRTDataSource/OGRVRTLayer/@name").string("countries"))
                .andExpect(xpath("/OGRVRTDataSource/OGRVRTLayer/SrcDataSource").string(startsWith('http://')))
                .andExpect(xpath("/OGRVRTDataSource/OGRVRTLayer/SrcDataSource").string(startsWith('http://')))
                .andExpect(xpath("/OGRVRTDataSource/OGRVRTLayer/LayerSRS").string("EPSG:4326"))
                .andExpect(xpath("/OGRVRTDataSource/OGRVRTLayer/SrcLayer").string("OGRGeoJSON"))

    }
}
