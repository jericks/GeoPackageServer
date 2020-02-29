<!DOCTYPE HTML>
<html>
<head>
    <title>GeoPackage Server</title>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <link rel="stylesheet" href="/furtive.min.css" type="text/css">
    <style>
        body {
            margin-left: 20px;
        }
    </style>
</head>
<body>

    <h1 class="fnt--blue">GeoPackage Server</h1>
    <div><a href="swagger-ui.html">API</a></div>

    <h3>Layers</h3>
    <ul class="layers">
        <#list layers as layer>
            <li><a href="layer/${layer}">${layer}</a></li>
        </#list>
    </ul>

    <h3>Tiles</h3>
    <ul class="tiles">
        <#list tiles as tile>
            <li><a href="tile/${tile}">${tile}</a></li>
        </#list>
    </ul>

</body>
</html>