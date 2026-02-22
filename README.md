# RSPSi-742
**<u>The following has been changed from the original**</u><br><br>
Added client-side 742 compatibility in:
- `Cache.java`
- `Chunk.java`
- `Client.java`
- `GameRasterizer.java`
- `KeyCombination.java`
- `MapRegion.java`
- `Mesh.java`
- `Mesh525.java`
- `Mesh622.java`
- `MeshLoader.java`
- `ObjectDefinition.java`

Converted `Plugin667` in `\plugins\` to `Plugin742` by extending and/or modifying:
- `AnimationDefLoader.java`  
- `FloorDefLoader.java`
- `ObjectDefLoader.java`
- `RSAreaLoaderOSRS.java`
- `SpotAnimationLoader.java`
- `TextureLoaderOSRS.java`
- `VarbitLoaderOSRS.java`
- `Plugin742.java` (from `Plugin667.java`)

All other plugin files remain the same as they were in the 667 build.

# Compatibility
- Use Oracle JDK / JRE 8.
- This has only been tested using the below resources. Results with other caches may vary.

# Disclaimers
- Some models may appear incorrect or have missing textures. This was somewhat present in the 667 build, but even moreso in 742.
- This project is at best an alpha, it pushes the limit of what the internal client can accomplish.
- If an estimate for "how functional is it?" were given, it would be `75%-80%` due to the remaining bugs.
  
# Resources
- [742 Cache](https://archive.openrs2.org/caches/runescape/544/disk.zip)
- [742 XTEAs](https://archive.openrs2.org/caches/runescape/544/keys.json)
- JDK: https://www.oracle.com/java/technologies/javase/javase8-archive-downloads.html
- JRE: https://www.java.com/en/download/manual.jsp  

This project uses JavaFX components, which are only available from the above linked JDK / JRE.

