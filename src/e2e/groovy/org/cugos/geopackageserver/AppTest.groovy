package org.cugos.geopackageserver

import org.apache.commons.io.FileUtils
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.openqa.selenium.By
import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.interactions.Action
import org.openqa.selenium.interactions.Actions
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner
import org.springframework.test.context.web.WebAppConfiguration
import static org.junit.Assert.*

@RunWith(SpringJUnit4ClassRunner)
@SpringBootTest(classes = App, webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT, properties = "server.port=8080")
@TestPropertySource(locations = "classpath:readWrite.properties")
class AppTest {

    private WebDriver browser

    private static File dir = new File("build/screenshots").absoluteFile

    @BeforeClass
    static void beforeClass() {
        File source = new File("src/e2e/resources/data.gpkg")
        File destination = new File("src/e2e/resources/data2.gpkg")
        FileUtils.copyFile(source, destination)

        FileUtils.deleteDirectory(dir)
        dir.mkdirs()
    }

    @AfterClass
    static void afterClass() {
        File file = new File("src/e2e/resources/data2.gpkg")
        file.delete()
    }

    @Before
    void before() {
        browser = new ChromeDriver()
    }

    @After
    void after() {
        browser.quit()
    }

    private void captureScreenShot(String name) {
        Thread.sleep(2 * 1000)
        File srcFile = ((TakesScreenshot)browser).getScreenshotAs(OutputType.FILE)
        File destFile = new File(dir, name + ".png")
        FileUtils.copyFile(srcFile, destFile)
    }

    @Test
    void homePageHasUrl() {
        browser.get("http://localhost:8080/")
        assertEquals("http://localhost:8080/", browser.getCurrentUrl())
        assertEquals("GeoPackage Server", browser.getTitle())
        captureScreenShot("web")
    }

    @Test
    void homePageHasHeader() {
        browser.get("http://localhost:8080/")
        String header = browser.findElement(By.tagName("h1")).getText()
        assertEquals("GeoPackage Server", header)
    }

    @Test
    void homePageHasLayers() {
        browser.get("http://localhost:8080/")
        WebElement e = browser.findElement(By.className("layers"))
        List<WebElement> items = e.findElements(By.tagName("li"))
        assertEquals(4, items.size())
        items.eachWithIndex { WebElement item, int i ->
            if (i == 0) {
                assertEquals "countries", item.text
            } else if (i == 1) {
                assertEquals "ocean", item.text
            } else if (i == 2) {
                assertEquals "places", item.text
            } else if (i == 3) {
                assertEquals "states", item.text
            }
        }
    }

    @Test
    void homePageHasTiles() {
        browser.get("http://localhost:8080/")
        WebElement e = browser.findElement(By.className("tiles"))
        List<WebElement> items = e.findElements(By.tagName("li"))
        assertEquals(2, items.size())
        items.eachWithIndex { WebElement item, int i ->
            if (i == 0) {
                assertEquals "world", item.text
            } else if (i == 1) {
                assertEquals "world_mercator", item.text
            }
        }
    }

    @Test
    void homePageCanGoToTilePage() {
        browser.get("http://localhost:8080/")
        WebElement e = browser.findElement(By.className("tiles"))
        List<WebElement> items = e.findElements(By.tagName("li"))
        List tileLayers = items.collect { WebElement li -> li.text }

        Closure goToTilePage = { String layer ->
            browser.get("http://localhost:8080/")
            WebElement tiles = browser.findElement(By.className("tiles"))
            List<WebElement> listItems = tiles.findElements(By.tagName("li"))
            WebElement listItem = listItems.find { WebElement item -> item.text.equalsIgnoreCase(layer)}
            listItem.findElement(By.tagName("a")).click()
            assertEquals("http://localhost:8080/tile/${layer}".toString(), browser.getCurrentUrl())
            captureScreenShot("tile_${layer}")
        }

        tileLayers.each { String layer ->
            goToTilePage(layer)
        }
    }

    @Test
    void homePageCanGoToLayerPage() {
        browser.get("http://localhost:8080/")
        WebElement e = browser.findElement(By.className("layers"))
        List<WebElement> items = e.findElements(By.tagName("li"))
        List vectorLayers = items.collect { WebElement li -> li.text }

        Closure goToLayerPage = { String layer ->
            browser.get("http://localhost:8080/")
            WebElement layers = browser.findElement(By.className("layers"))
            List<WebElement> listItems = layers.findElements(By.tagName("li"))
            WebElement listItem = listItems.find { WebElement item -> item.text.equalsIgnoreCase(layer)}
            listItem.findElement(By.tagName("a")).click()
            assertEquals("http://localhost:8080/layer/${layer}".toString(), browser.getCurrentUrl())
            captureScreenShot("layer_${layer}")
        }

        vectorLayers.each { String layer ->
            goToLayerPage(layer)
        }
    }

    @Test
    void tileWorldMetadata() {
        browser.get("http://localhost:8080/tile/world")
        WebElement e = browser.findElement(By.className("metadata"))
        List<WebElement> divs = e.findElements(By.tagName("div"))
        assertEquals("Name = world", divs[0].getText())
        assertEquals("Projection = EPSG:4326", divs[1].getText())
        assertEquals("Bounds = -179.99, -89.99, 179.99, 89.99 EPSG:4326", divs[2].getText())
        assertEquals("Tile Size = 256 x 256", divs[3].getText())
    }

    @Test
    void tileWorldMercatorMetadata() {
        browser.get("http://localhost:8080/tile/world_mercator")
        WebElement e = browser.findElement(By.className("metadata"))
        List<WebElement> divs = e.findElements(By.tagName("div"))
        assertEquals("Name = world_mercator", divs[0].getText())
        assertEquals("Projection = EPSG:3857", divs[1].getText())
        assertEquals("Bounds = -20,036,395.148, -20,037,471.205, 20,036,395.148, 20,037,471.205 EPSG:3857", divs[2].getText())
        assertEquals("Tile Size = 256 x 256", divs[3].getText())
    }


    @Test
    void tileStatsWorldMercator() {
        browser.get("http://localhost:8080/tile/world_mercator")
        WebElement e = browser.findElement(By.className("tilestats"))
        List<WebElement> rows = e.findElement(By.tagName("tbody")).findElements(By.tagName("tr"))
        Closure checkRow = { WebElement row, int zoom, int total, int tiles, String percent  ->
            List<WebElement> cells = row.findElements(By.tagName("td"))
            assertEquals(zoom, cells.get(0).getText() as int)
            assertEquals(total, cells.get(1).getText() as int)
            assertEquals(tiles, cells.get(2).getText() as int)
            assertEquals(percent, cells.get(3).getText())
        }
        rows.eachWithIndex { WebElement row, int index ->
            checkRow(row, index, Math.pow(4, index) as int, Math.pow(4, index) as int, "100%")
        }
    }

    @Test
    void tileStatsWorld() {
        browser.get("http://localhost:8080/tile/world")
        WebElement e = browser.findElement(By.className("tilestats"))
        List<WebElement> rows = e.findElement(By.tagName("tbody")).findElements(By.tagName("tr"))
        Closure checkRow = { WebElement row, int zoom, int total, int tiles, String percent  ->
            List<WebElement> cells = row.findElements(By.tagName("td"))
            assertEquals(zoom, cells.get(0).getText() as int)
            assertEquals(total, cells.get(1).getText() as int)
            assertEquals(tiles, cells.get(2).getText() as int)
            assertEquals(percent, cells.get(3).getText())
        }

        List data = [
                [zoom: 0, total: 2,   tiles: 2,   percent: 100],
                [zoom: 1, total: 8,   tiles: 8,   percent: 100],
                [zoom: 2, total: 32,  tiles: 32,  percent: 100],
                [zoom: 3, total: 128, tiles: 128, percent: 100]
        ]

        rows.eachWithIndex { WebElement row, int index ->
            checkRow(row, data[index].zoom, data[index].total, data[index].tiles, data[index].percent + '%')
        }
    }

    @Test
    void mapTileWorld() {
        browser.get("http://localhost:8080/tile/world")
        WebElement map = browser.findElement(By.id("map"))
        WebElement zoomIn = map.findElement(By.className("ol-zoom-in"))
        zoomIn.click()
        Thread.sleep(250)
        zoomIn.click()
        Thread.sleep(250)
        captureScreenShot("tile_map_world_zoom_in")
        WebElement zoomOut = map.findElement(By.className("ol-zoom-out"))
        zoomOut.click()
        Thread.sleep(250)
        zoomOut.click()
        Thread.sleep(250)
        captureScreenShot("tile_map_world_zoom_out")
    }

    @Test
    void mapTileWorldMercator() {
        browser.get("http://localhost:8080/tile/world_mercator")
        WebElement map = browser.findElement(By.id("map"))
        WebElement zoomIn = map.findElement(By.className("ol-zoom-in"))
        zoomIn.click()
        Thread.sleep(250)
        zoomIn.click()
        Thread.sleep(250)
        captureScreenShot("tile_map_world_mercator_zoom_in")
        WebElement zoomOut = map.findElement(By.className("ol-zoom-out"))
        zoomOut.click()
        Thread.sleep(250)
        zoomOut.click()
        Thread.sleep(250)
        captureScreenShot("tile_map_world_mercator_zoom_out")
    }

    @Test
    void pyramidForWorld() {
        browser.get("http://localhost:8080/tile/world")
        WebElement div = browser.findElement(By.className("pyramid"))
        assertEquals "Pyramid", div.findElement(By.tagName("h4")).getText()
        WebElement table = div.findElement(By.tagName("table"))
        assertEquals(["Zoom", "Columns", "Rows", "Tiles", "X Resolution", "Y Resolution"], table.findElement(By.tagName("thead")).findElements(By.tagName("th")).collect { it.text })
        assertEquals 20, table.findElement(By.tagName("tbody")).findElements(By.tagName("tr")).size()
        assertEquals(["0", "2", "1", "2", "0.703125", "0.703125"], table.findElement(By.tagName("tbody")).findElements(By.tagName("tr"))[0].findElements(By.tagName("td")).collect { it.text })
    }

    @Test
    void pyramidForWorldMeractor() {
        browser.get("http://localhost:8080/tile/world_mercator")
        WebElement div = browser.findElement(By.className("pyramid"))
        assertEquals "Pyramid", div.findElement(By.tagName("h4")).getText()
        WebElement table = div.findElement(By.tagName("table"))
        assertEquals(["Zoom", "Columns", "Rows", "Tiles", "X Resolution", "Y Resolution"], table.findElement(By.tagName("thead")).findElements(By.tagName("th")).collect { it.text })
        assertEquals 20, table.findElement(By.tagName("tbody")).findElements(By.tagName("tr")).size()
        assertEquals(["0", "1", "1", "1", "156412", "156412"], table.findElement(By.tagName("tbody")).findElements(By.tagName("tr"))[0].findElements(By.tagName("td")).collect { it.text })
    }

    @Test
    void pyramidWebServiceForWorld() {
        browser.get("http://localhost:8080/tile/world")
        browser.findElement(By.linkText("Pyramid")).click()
        assertEquals("http://localhost:8080/tiles/world/pyramid", browser.getCurrentUrl())
        captureScreenShot("tile_world_pyramid_service")
    }

    @Test
    void pyramidWebServiceForWorldMercator() {
        browser.get("http://localhost:8080/tile/world_mercator")
        browser.findElement(By.linkText("Pyramid")).click()
        assertEquals("http://localhost:8080/tiles/world_mercator/pyramid", browser.getCurrentUrl())
        captureScreenShot("tile_world_mercator_pyramid_service")
    }

    @Test
    void tileWebServiceForWorld() {
        browser.get("http://localhost:8080/tile/world")
        browser.findElement(By.linkText("Tiles")).click()
        assertEquals("http://localhost:8080/tiles/world/tile/0/0/0", browser.getCurrentUrl())
        captureScreenShot("tile_world_tile_service")
    }

    @Test
    void tileWebServiceForWorldMercator() {
        browser.get("http://localhost:8080/tile/world_mercator")
        browser.findElement(By.linkText("Tiles")).click()
        assertEquals("http://localhost:8080/tiles/world_mercator/tile/0/0/0", browser.getCurrentUrl())
        captureScreenShot("tile_world_mercator_tile_service")
    }


    @Test
    void rasterWebServiceForWorld() {
        browser.get("http://localhost:8080/tile/world")
        browser.findElement(By.linkText("Raster")).click()
        assertEquals("http://localhost:8080/tiles/world/raster/400/400/-156.533203,3.688855,-50.712891,56.800878", browser.getCurrentUrl())
        captureScreenShot("tile_world_raster_service")
    }

    @Test
    void rasterWebServiceForWorldMercator() {
        browser.get("http://localhost:8080/tile/world_mercator")
        browser.findElement(By.linkText("Raster")).click()
        assertEquals("http://localhost:8080/tiles/world_mercator/raster/400/400/-156.533203,3.688855,-50.712891,56.800878", browser.getCurrentUrl())
        captureScreenShot("tile_world_meractor_raster_service")
    }

    @Test
    void tileStatsWebServiceForWorld() {
        browser.get("http://localhost:8080/tile/world")
        browser.findElement(By.linkText("Tile Counts")).click()
        assertEquals("http://localhost:8080/tiles/world/tile/counts", browser.getCurrentUrl())
        captureScreenShot("tile_world_tile_counts_service")
    }

    @Test
    void tileStatsWebServiceForWorldMercator() {
        browser.get("http://localhost:8080/tile/world_mercator")
        browser.findElement(By.linkText("Tile Counts")).click()
        assertEquals("http://localhost:8080/tiles/world_mercator/tile/counts", browser.getCurrentUrl())
        captureScreenShot("tile_world_mercator_tile_counts_service")
    }

    @Test
    void qgisLayerWebServiceForWorld() {
        browser.get("http://localhost:8080/tile/world")
        browser.findElement(By.linkText("QGIS Layer")).click()
        assertEquals("http://localhost:8080/tiles/world/gdal", browser.getCurrentUrl())
        captureScreenShot("tile_world_gdal_service")
    }

    @Test
    void qgisLayerWebServiceForWorldMercator() {
        browser.get("http://localhost:8080/tile/world_mercator")
        browser.findElement(By.linkText("QGIS Layer")).click()
        assertEquals("http://localhost:8080/tiles/world_mercator/gdal", browser.getCurrentUrl())
        captureScreenShot("tile_world_meractor_gdal_service")
    }

    @Test
    void displayMetadataforLayerCountries() {
        browser.get("http://localhost:8080/layer/countries")
        WebElement div = browser.findElement(By.className("metadata"))
        assertEquals "Metadata", div.findElement(By.tagName("h4")).text
        List<WebElement> divs = div.findElements(By.tagName("div"))
        assertEquals 4, divs.size()
        assertEquals "Name = countries", divs[0].text
        assertEquals "Projection = EPSG:4326", divs[1].text
        assertEquals "Count = 177", divs[2].text
        assertEquals "Bounds = -180, -90, 180, 83.645 EPSG:4326", divs[3].text
    }

    @Test
    void displayFieldsforLayerCountries() {
        browser.get("http://localhost:8080/layer/countries")
        WebElement div = browser.findElement(By.className("fields"))
        assertEquals "Fields", div.findElement(By.tagName("h4")).text
        WebElement table = div.findElement(By.tagName("table"))
        assertEquals(["Name", "Type"], table.findElement(By.tagName("thead")).findElements(By.tagName("th")).collect { it.text })
        assertEquals 47, table.findElement(By.tagName("tbody")).findElements(By.tagName("tr")).size()
        assertEquals(["the_geom","MultiPolygon"], table.findElement(By.tagName("tbody")).findElements(By.tagName("tr"))[0].findElements(By.tagName("td")).collect { it.text })
    }

    @Test
    void displayMapForLayerCountries() {
        browser.get("http://localhost:8080/layer/countries")
        WebElement map = browser.findElement(By.id("map"))
        WebElement zoomIn = map.findElement(By.className("ol-zoom-in"))
        zoomIn.click()
        Thread.sleep(250)
        zoomIn.click()
        Thread.sleep(250)
        captureScreenShot("layer_map_countries_zoom_in")
        WebElement zoomOut = map.findElement(By.className("ol-zoom-out"))
        zoomOut.click()
        Thread.sleep(250)
        zoomOut.click()
        Thread.sleep(250)
        captureScreenShot("layer_map_countries_zoom_out")
    }

    @Test
    void identifyOnMapForLayerCountries() {
        browser.get("http://localhost:8080/layer/countries")
        WebElement map = browser.findElement(By.id("map"))
        new Actions(browser)
                .moveToElement(map, 114,192)
                .click().perform()
        Thread.sleep(2000)
        captureScreenShot("layer_map_countries_id")
    }

    @Test
    void clickOnSchemaWebServiceForLayerCountries() {
        browser.get("http://localhost:8080/layer/countries")
        browser.findElement(By.linkText("Schema")).click()
        assertEquals("http://localhost:8080/layers/countries/schema", browser.getCurrentUrl())
        captureScreenShot("layer_countries_schema_web_services")
    }

    @Test
    void clickOnCountWebServiceForLayerCountries() {
        browser.get("http://localhost:8080/layer/countries")
        browser.findElement(By.linkText("Count")).click()
        assertEquals("http://localhost:8080/layers/countries/count", browser.getCurrentUrl())
        captureScreenShot("layer_countries_count_web_services")
    }

    @Test
    void clickOnBoundsWebServiceForLayerCountries() {
        browser.get("http://localhost:8080/layer/countries")
        browser.findElement(By.linkText("Bounds")).click()
        assertEquals("http://localhost:8080/layers/countries/bounds", browser.getCurrentUrl())
        captureScreenShot("layer_countries_bounds_web_services")
    }

    @Test
    void clickOnFeaturesGeoJsonWebServiceForLayerCountries() {
        browser.get("http://localhost:8080/layer/countries")
        browser.findElement(By.linkText("Features (GeoJson)")).click()
        assertEquals("http://localhost:8080/layers/countries/features.json", browser.getCurrentUrl())
        captureScreenShot("layer_countries_features_geojson_web_services")
    }

    @Test
    void clickOnFeatureVectorTileGeoJsonWebServiceForLayerCountries() {
        browser.get("http://localhost:8080/layer/countries")
        browser.findElement(By.linkText("Feature Vector Tile (GeoJson)")).click()
        assertEquals("http://localhost:8080/layers/countries/tile/json/2/1/1", browser.getCurrentUrl())
        captureScreenShot("layer_countries_feature_vector_tile_geojson_web_services")
    }

    @Test
    void qgisLayerWebServiceForCountries() {
        browser.get("http://localhost:8080/layer/countries")
        browser.findElement(By.linkText("QGIS Layer")).click()
        assertEquals("http://localhost:8080/layers/countries/gdal", browser.getCurrentUrl())
        captureScreenShot("layer_countries_gdal_service")
    }

    @Test
    void switchMapVectorLayers() {
        browser.get("http://localhost:8080/layer/countries")
        browser.findElement(By.linkText("JSON")).click()
        captureScreenShot("layer_countries_layer_json")
        browser.findElement(By.linkText("JSON Tiles")).click()
        captureScreenShot("layer_countries_layer_jsontiles")
        browser.findElement(By.linkText("PBF Tiles")).click()
        captureScreenShot("layer_countries_layer_pbf")
    }
}
