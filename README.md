# RSPSi-742
<div align="left">
  <video src="https://i.imgur.com/bxjQ9k1.mp4" controls width="600"></video>
</div>

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
- This build is **<u>EXPERIMENTAL</u>**, it pushes the limit of what the internal client can achieve.
<br><br>
- This build **WILL** fire console errors while you work, mostly around mesh face issues. The majority of them can be ignored unless workspace issues occur.
  <br><br>
- Some models may appear incorrect or have missing textures. This was somewhat present in the 667 build, but even more so in 742.
  <br><br>
- Your real client will not fail to decode meshes the same way this might, however, always double-check once you import that nothing was missed or removed.
  <br><br>
- RSPSi does not contain a method to modify under-map landscape. This is usually a third data file, as it sits, maps with water will not have under-map landscape generated and will appear incorrect with HD water enabled.
  <br><br>
- If an estimate for "how functional is this?" were given, it would be `75%-80%` due to the remaining bugs.
  <br><br>

**<u><i>ALWAYS back up your cache before importing map files to it!</i></u>**
  
# Resources
- [742 Cache](https://archive.openrs2.org/caches/runescape/544/disk.zip)
- [742 XTEAs](https://archive.openrs2.org/caches/runescape/544/keys.json)
- JDK: https://www.oracle.com/java/technologies/javase/javase8-archive-downloads.html
- JRE: https://www.java.com/en/download/manual.jsp  

This project uses JavaFX components, which are only available from the above linked JDK / JRE.

