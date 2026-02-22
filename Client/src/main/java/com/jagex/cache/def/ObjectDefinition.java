package com.jagex.cache.def;

import org.displee.cache.index.Index;

import java.util.Arrays;

import com.jagex.Client;
import com.jagex.cache.graphics.Sprite;
import com.jagex.cache.loader.anim.FrameLoader;
import com.jagex.entity.model.Mesh;
import com.jagex.entity.model.MeshLoader;
import com.jagex.net.ResourceProvider;
import com.rspsi.cache.CacheFileType;
import com.rspsi.misc.FixedIntegerKeyMap;
import com.rspsi.misc.FixedLongKeyMap;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class ObjectDefinition {

	// Thanks Super_

	public static FixedIntegerKeyMap<Mesh> baseModels = new FixedIntegerKeyMap<Mesh>(500);
	public static Client client;
	public static boolean lowMemory;
	public static FixedLongKeyMap<Mesh> models = new FixedLongKeyMap<Mesh>(500);
	private static Mesh[] parts = new Mesh[4];


	public static void dispose() {
		baseModels.clear();
		models.clear();
	}
	
	private Sprite mapFunctionSprite, mapSceneSprite;
	
	public void generateSprites(Index spriteIndex) {
		
	}

	private byte ambientLighting;
	private int animation;
	private boolean castsShadow;
	private boolean contouredGround;
	private int decorDisplacement;
	private boolean delayShading;
	private byte[] description;
	private boolean hollow;
	private int id = -1;
	private boolean impenetrable;
	private String[] interactions;
	private boolean interactive;
	private boolean inverted;
	private int length;
	private byte lightDiffusion;
	private int mapscene;
	private int minimapFunction;
	private int[] modelIds;
	private int[] modelTypes;
	private int[] morphisms;
	private int varbit;
	private int varp;
	private String name;
	private boolean obstructsGround;
	private boolean occludes;
	private int[] originalColours;
	private int[] replacementColours;
	private int[] retextureToFind;
	private int[] textureToReplace;
	private int scaleX;
	private int scaleY;
	private int scaleZ;
	private boolean solid;
	private int supportItems;
	private int surroundings;
	private int translateX;
	private int translateY;
	private int translateZ;
	private int width;
	
	private int areaId = -1;

	public int[] getModelIds() {
		if(modelIds != null && modelTypes != null && modelTypes[0] == 22 && modelIds[0] == 1105 && (areaId != -1 || minimapFunction != -1))
			return new int[] {111};
		return modelIds;
	}

	public String getName() {
		return (name == null || name.equals("") ? minimapFunction != -1 ? "minimap-function:" + minimapFunction : "null" : name + ( minimapFunction != -1 ? " minimap-function:" + minimapFunction : "")) ;
	}


	public final void loadModels(ResourceProvider provider) {
		if (getModelIds() != null) {
			for (int id : getModelIds()) {
				provider.requestFile(CacheFileType.MODEL, id);
			}
		}
	}

	private final Mesh model(int type, int frame, int orientation) {
		Mesh base = null;
		long key;
		if (modelTypes == null) {
			if (type != 10)
				return null;

			key = frame + 1L << 32 | ((inverted ? 1 : 0) << 16)| id << 6 | orientation;
			Mesh model = models.get(key);
			if (model != null)
				return model;

			int[] modelIds = getModelIds();
			if (modelIds == null)
				return null;

			boolean invert = inverted ^ orientation > 3;
			int count = modelIds.length;
			Mesh[] modelParts = count > 1 ? new Mesh[count] : null;
			for (int index = 0; index < count; index++) {
				int modelId = modelIds[index];
				if (invert) {
					modelId |= 0x10000;
				}

				base = baseModels.get(modelId);
				if (base == null) {
					base = MeshLoader.getSingleton().lookup(modelId & 0xffff);
					if (base == null) {
						return null;
					}
					base = base.copy();

					if (invert) {
						base.invert();
						//System.out.println("INVERTING");
					}

					baseModels.put(modelId, base);
				}

				if (count > 1) {
					modelParts[index] = base;
				}
			}

			if (count > 1) {
				base = new Mesh(count, modelParts);
			}
		} else {
			int[] allModelIds = getModelIds();
			if (allModelIds == null) {
				return null;
			}
			int[] matchedIndices = new int[modelTypes.length];
			int matchedCount = 0;
			for (int i = 0; i < modelTypes.length; i++) {
				if (i < allModelIds.length && modelTypes[i] == type && allModelIds[i] >= 0) {
					matchedIndices[matchedCount++] = i;
				}
			}

			if (matchedCount == 0)
				return null;

			key = frame + 1L << 32 | ((inverted ? 1 : 0) << 16) | id << 6 | type << 3 | orientation;
			for (int i = 0; i < matchedCount; i++) {
				key = key * 67783L + allModelIds[matchedIndices[i]];
			}
			Mesh model = models.get(key);
			if (model != null)
				return model;

			boolean invert = inverted ^ orientation > 3;
			Mesh[] modelParts = matchedCount > 1 ? new Mesh[matchedCount] : null;
			for (int i = 0; i < matchedCount; i++) {
				int modelId = allModelIds[matchedIndices[i]];
				if (invert) {
					modelId |= 0x10000;
				}
				base = baseModels.get(modelId);
				if (base == null) {
					base = MeshLoader.getSingleton().lookup(modelId & 0xffff);
					if (base == null) {
						return null;
					}
					base = base.copy();
					if (invert) {
						base.invert();
						//System.out.println("INVERTING");
					}
					baseModels.put(modelId, base);
				}
				if (matchedCount > 1) {
					modelParts[i] = base;
				}
			}

			if (matchedCount > 1) {
				base = new Mesh(matchedCount, modelParts);
			}
		}

		boolean scale = scaleX != 128 || scaleY != 128 || scaleZ != 128;
		boolean translate = translateX != 0 || translateY != 0 || translateZ != 0;

		Mesh model = new Mesh(base, originalColours == null, FrameLoader.isInvalid(frame),
				orientation == 0 && frame == -1 && !scale && !translate, retextureToFind == null);
		if (frame != -1) {
			model.prepareSkeleton();
			model.apply(frame);
			model.faceGroups = null;
			model.vertexGroups = null;
		}
		
		if(type == 4 && orientation > 3) {//OSRS
			model.pitch(256);
			model.offsetVertices(45, 0, -45);
		}
		
		orientation &= 3;

		while (orientation-- > 0) {
			model.rotateClockwise();
		}

		if (originalColours != null) {
			for (int colour = 0; colour < originalColours.length; colour++) {
				model.recolour(originalColours[colour], replacementColours[colour]);
			}

		}

		if(retextureToFind != null){
			for(int index = 0;index < retextureToFind.length;index++)
				model.retexture(retextureToFind[index], textureToReplace[index]);
		}
		if (scale) {
			model.scale(scaleX, scaleZ, scaleY);
		}

		if (translate) {
			model.translate(translateX, translateY, translateZ);
		}

		model.light(64 + ambientLighting, 768 + lightDiffusion * 25, -50, -10, -50, !delayShading);
		if (supportItems == 1) {
			model.anInt1654 = model.getModelHeight();
		}
		models.put(key, model);
		return model;
	}

	public final Mesh modelAt(int type, int orientation, int aY, int bY, int cY, int dY, int frameId) {
		Mesh model = model(type, frameId, orientation);
		if (model == null) {
			
			//System.out.println("fail1 " + type + ":" + frameId + ":" + orientation);
			return null;
		}

		if (contouredGround || delayShading) {
			model = new Mesh(contouredGround, delayShading, model);
		}

		if (contouredGround) {
			int y = (aY + bY + cY + dY) / 4;
			for (int vertex = 0; vertex < model.numVertices; vertex++) {
				int x = model.verticesX[vertex];
				int z = model.verticesZ[vertex];
				int l2 = aY + (bY - aY) * (x + 64) / 128;
				int i3 = dY + (cY - dY) * (x + 64) / 128;
				int j3 = l2 + (i3 - l2) * (z + 64) / 128;
				model.verticesY[vertex] += j3 - y;
			}

			model.computeSphericalBounds();
		}
		return model;
	}

	public boolean obstructsGround() {
		return obstructsGround;
	}

	public boolean occludes() {
		return occludes;
	}

	public final boolean ready() {
		if (getModelIds() == null)
			return true;
		boolean ready = true;
		for (int id : getModelIds()) {
			ready &= MeshLoader.getSingleton().loaded(id);
		}

		return ready;
	}

	protected int modelTries = 0;
	
	public final boolean readyOrThrow(int type) throws Exception {
		if (modelTypes == null) {
			if (getModelIds() == null || type != 10)
				return true;

			boolean ready = true;
			for (int id : getModelIds()) {
				ready &= MeshLoader.getSingleton().loaded(id);
			}
			if(ready)
				modelTries = 0;
			else
				modelTries++;
			if(modelTries > 500)
				throw new Exception("Model missing");
			return ready;
		}

		for (int index = 0; index < modelTypes.length; index++) {
			if (modelTypes[index] == type) {
				int[] modelIds = getModelIds();
				if (modelIds == null) {
					modelTries = 0;
					return true;
				}
				boolean ready = true;
				for (int i = index; i < modelTypes.length; i++) {
					if (modelTypes[i] == type && i < modelIds.length) {
						int modelId = modelIds[i];
						if (modelId >= 0) {
							ready &= MeshLoader.getSingleton().loaded(modelId);
						}
					}
				}
				if(ready)
					modelTries = 0;
				else
					modelTries++;

				if(modelTries > 500)
					throw new Exception("Model missing");
				return ready;
			}
		}
		modelTries = 0;
		return true;
	}
	
	public final boolean ready(int type) {
		if (modelTypes == null) {
			if (getModelIds() == null || type != 10)
				return true;

			boolean ready = true;
			for (int id : getModelIds()) {
				ready &= MeshLoader.getSingleton().loaded(id);
			}
			if(ready)
				modelTries = 0;
			else
				modelTries++;
			return ready || modelTries > 500;
		}

		for (int index = 0; index < modelTypes.length; index++) {
			if (modelTypes[index] == type) {
				int[] modelIds = getModelIds();
				if (modelIds == null) {
					modelTries = 0;
					return true;
				}
				boolean ready = true;
				for (int i = index; i < modelTypes.length; i++) {
					if (modelTypes[i] == type && i < modelIds.length) {
						int modelId = modelIds[i];
						if (modelId >= 0) {
							ready &= MeshLoader.getSingleton().loaded(modelId);
						}
					}
				}

				if(ready)
					modelTries = 0;
				else
					modelTries++;
				return ready || modelTries > 500;
			}
		}
		modelTries = 0;
		return true;
	}

	public final void reset() {
		modelIds = null;
		modelTypes = null;
		name = null;
		description = null;
		originalColours = null;
		replacementColours = null;
		retextureToFind = null;
		textureToReplace = null;
		width = 1;
		length = 1;
		solid = true;
		impenetrable = true;
		interactive = false;
		contouredGround = false;
		delayShading = false;
		occludes = false;
		animation = -1;
		decorDisplacement = 16;
		ambientLighting = 0;
		lightDiffusion = 0;
		interactions = null;
		minimapFunction = -1;
		mapscene = -1;
		inverted = false;
		castsShadow = true;
		scaleX = 128;
		scaleY = 128;
		scaleZ = 128;
		surroundings = 0;
		translateX = 0;
		translateY = 0;
		translateZ = 0;
		obstructsGround = false;
		hollow = false;
		supportItems = -1;
		varbit = -1;
		varp = -1;
		morphisms = null;
	}

	public int getHash() {
		final int prime = 337;
		int result = 1;
		result = prime * result + ambientLighting;
		result = prime * result + animation;
		result = prime * result + (castsShadow ? 1231 : 1237);
		result = prime * result + (contouredGround ? 1231 : 1237);
		result = prime * result + decorDisplacement;
		result = prime * result + (delayShading ? 1231 : 1237);
		result = prime * result + (hollow ? 1231 : 1237);
		result = prime * result + (impenetrable ? 1231 : 1237);
		result = prime * result + Arrays.hashCode(interactions);
		result = prime * result + (interactive ? 1231 : 1237);
		result = prime * result + (inverted ? 1231 : 1237);
		result = prime * result + length;
		result = prime * result + lightDiffusion;
		result = prime * result + mapscene;
		result = prime * result + minimapFunction;
		result = prime * result + Arrays.hashCode(modelIds);
		result = prime * result + Arrays.hashCode(modelTypes);
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + (obstructsGround ? 1231 : 1237);
		result = prime * result + (occludes ? 1231 : 1237);
		result = prime * result + Arrays.hashCode(originalColours);
		result = prime * result + Arrays.hashCode(replacementColours);
		result = prime * result + Arrays.hashCode(retextureToFind);
		result = prime * result + scaleX;
		result = prime * result + scaleY;
		result = prime * result + scaleZ;
		result = prime * result + (solid ? 1231 : 1237);
		result = prime * result + supportItems;
		result = prime * result + surroundings;
		result = prime * result + Arrays.hashCode(textureToReplace);
		result = prime * result + translateX;
		result = prime * result + translateY;
		result = prime * result + translateZ;
		result = prime * result + varbit;
		result = prime * result + varp;
		result = prime * result + width;
		return result;
	}


}
