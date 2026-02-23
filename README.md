# RSPSi-742
![Custom Lletya made with RSPSi-742](https://i.imgur.com/vHNucy9.jpeg)
###### Custom Lletya made with RSPSi-742
____________________________________________________________________
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
- This build is **<u>EXPERIMENTAL</u>**, it (in my opinion) pushes the limit of what the internal client can achieve.
<br><br>
- This build **WILL** fire console errors while you work, mostly around mesh face issues. The majority of them can be ignored unless workspace issues occur.
  <br><br>
- Some models may appear incorrect or have missing textures. This was somewhat present in the 667 build, but even more so in 742. These issues are the cause of the console errors.
  <br><br>
- Your real client will not fail to decode meshes the same way this might, however, always double-check once you import that nothing was missed or removed.
  <br><br>
- RSPSi does not contain a method to modify under-map landscape. This is usually (I think?) a third data file. As it sits, maps with water will not have under-map landscape generated and will appear incorrect with HD water enabled (Only if you **ADD** water in a map with **NO WATER** or a map that started as **deep ocean and is out-of-bounds**!)
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

# Credits
- [The original RSPSi](https://github.com/RSPSi/RSPSi) - Creating the editor.
- [2011Scape/RSPSi-667](https://github.com/2011Scape/RSPSi-667) - Extending the editor closer to the state needed for 742.
- [LostCityRS/RS742](https://github.com/LostCityRS/RS742) - Amazing deobfuscated 742 client used for reference to make this work.

A massive thank-you to all the talented developers who put their time and effort into these projects which let me get this done. It could never have been done without their hard work.

