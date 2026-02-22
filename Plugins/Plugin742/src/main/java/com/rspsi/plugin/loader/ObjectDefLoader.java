package com.rspsi.plugin.loader;

import com.google.common.collect.Maps;
import com.jagex.Client;
import com.jagex.cache.config.VariableBits;
import com.jagex.cache.def.ObjectDefinition;
import com.jagex.cache.loader.config.VariableBitLoader;
import com.jagex.cache.loader.object.ObjectDefinitionLoader;
import com.jagex.io.Buffer;
import com.jagex.util.ByteBufferUtils;
import lombok.extern.slf4j.Slf4j;
import org.displee.cache.index.Index;
import org.displee.cache.index.archive.Archive;
import org.displee.cache.index.archive.file.File;
import org.displee.utilities.Miscellaneous;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class ObjectDefLoader extends ObjectDefinitionLoader {

	private Map<Integer, ObjectDefinition> definitions = Maps.newHashMap();

	@Override
	public void init(Archive archive) {

	}

	@Override
	public void init(Buffer data, Buffer indexBuffer) {

	}

	private int size;

	public void decodeObjects(Index index) {
		definitions.clear();
		if (index == null || index.getLastArchive() == null || index.getLastArchive().getLastFile() == null) {
			size = 0;
			return;
		}
		size = index.getLastArchive().getId() * 256 + index.getLastArchive().getLastFile().getId() + 1;
		for (int id = 0; id < size; id++) {
			int archiveId = Miscellaneous.getConfigArchive(id, 8);
			Archive archive = index.getArchive(archiveId);
			if (Objects.nonNull(archive)) {
				int fileId = Miscellaneous.getConfigFile(id, 8);
				File file = archive.getFile(fileId);
				if (Objects.nonNull(file) && Objects.nonNull(file.getData())) {
					try {
						ObjectDefinition def = decode(id, ByteBuffer.wrap(file.getData()));
						definitions.put(id, def);
					} catch (Exception ex) {
						log.error("Failed decoding object definition {}", id, ex);
					}
				}
			}
		}
	}

	public ObjectDefinition decode(int id, ByteBuffer buffer) {
		ObjectDefinition definition = new ObjectDefinition();
		definition.reset();
		definition.setId(id);
		int lastOpcode = -1;
		boolean explicitInteractive = false;
		try {
			for (;;) {
				int opcode = buffer.get() & 0xff;

				if (opcode == 0)
					break;
				if (opcode == 1) {
					int typeSize = buffer.get() & 0xff;
					int[] modelIds = new int[Math.max(1, typeSize)];
					int[] modelTypes = new int[Math.max(1, typeSize)];
					int totalModels = 0;
					for (int type = 0; type < typeSize; type++) {
						int modelType = buffer.get();
						int modelsLength = buffer.get() & 0xff;
						for (int model = 0; model < modelsLength; model++) {
							int modelId = readSmart2Or4Null(buffer);
							if (modelId < 0) {
								continue;
							}
							if (totalModels >= modelIds.length) {
								int newSize = Math.max(modelIds.length * 2, totalModels + 1);
								modelIds = Arrays.copyOf(modelIds, newSize);
								modelTypes = Arrays.copyOf(modelTypes, newSize);
							}
							modelIds[totalModels] = modelId;
							modelTypes[totalModels] = modelType;
							totalModels++;
						}
					}
					if (totalModels == 0) {
						definition.setModelIds(null);
						definition.setModelTypes(null);
					} else {
						definition.setModelIds(Arrays.copyOf(modelIds, totalModels));
						definition.setModelTypes(Arrays.copyOf(modelTypes, totalModels));
					}
				} else if (opcode == 5) {
					int count = buffer.get() & 0xff;
					int[] modelIds = new int[Math.max(1, count)];
					int totalModels = 0;
					for (int i = 0; i < count; i++) {
						int modelId = readSmart2Or4Null(buffer);
						if (modelId < 0) {
							continue;
						}
						modelIds[totalModels++] = modelId;
					}
					definition.setModelIds(totalModels == 0 ? null : Arrays.copyOf(modelIds, totalModels));
					definition.setModelTypes(null);
				} else if (opcode == 2) {
					definition.setName(ByteBufferUtils.getOSRSString(buffer));
				} else if (opcode == 14) {
					definition.setWidth(buffer.get() & 0xff);
				} else if (opcode == 15) {
					definition.setLength(buffer.get() & 0xff);
				} else if (opcode == 17) {
					definition.setSolid(false);
				} else if (opcode == 18) {
					definition.setImpenetrable(false);
				} else if (opcode == 19) { // x
					explicitInteractive = true;
					int interactive = buffer.get() & 0xff;
					definition.setInteractive(interactive == 1);
				} else if (opcode == 21) { // x
					definition.setContouredGround(true);
				} else if (opcode == 22) {
					definition.setDelayShading(true);
				} else if (opcode == 23) {
					definition.setOccludes(true);
				} else if (opcode == 24) {
					definition.setAnimation(readSmart2Or4Null(buffer));
				} else if (opcode == 27) { // x
					//setInteractType(1);
				} else if (opcode == 28) { // x
					definition.setDecorDisplacement((buffer.get() & 0xff) << 2);
				} else if (opcode == 29) {
					definition.setAmbientLighting((byte) (buffer.get()));
				} else if (opcode == 39) {
					definition.setLightDiffusion((byte) (buffer.get()));
				} else if (opcode >= 30 && opcode < 39) {
					String[] interactions = definition.getInteractions();
					if (interactions == null) {
						interactions = new String[10];
					}
					interactions[opcode - 30] = ByteBufferUtils.getOSRSString(buffer);
					if (interactions[opcode - 30].equalsIgnoreCase("hidden")) {
						interactions[opcode - 30] = null;
					}
					definition.setInteractions(interactions);
				} else if (opcode == 40) { // x
					int count = buffer.get() & 0xff;
					int[] originalColours = new int[count];
					int[] replacementColours = new int[count];
					for (int i = 0; i < count; i++) {
						originalColours[i] = buffer.getShort() & 0xffff;
						replacementColours[i] = buffer.getShort() & 0xffff;
					}
					definition.setOriginalColours(originalColours);
					definition.setReplacementColours(replacementColours);
				} else if (opcode == 41) {
					int count = buffer.get() & 0xff;
					int[] retextureFrom = new int[count];
					int[] retextureTo = new int[count];
					for (int i = 0; i < count; i++) {
						retextureFrom[i] = buffer.getShort() & 0xffff;
						retextureTo[i] = buffer.getShort() & 0xffff;
					}
					definition.setRetextureToFind(retextureFrom);
					definition.setTextureToReplace(retextureTo);
				} else if (opcode == 42) {
					int i = buffer.get() & 0xff;
					for (int index = 0; index < i; index++)
						buffer.get();
				} else if (opcode == 44) {
					int i = buffer.getShort() & 0xffff;
				} else if (opcode == 45) {
					int i = buffer.getShort() & 0xffff;
				} else if (opcode == 60) {
					int minimapFunction = buffer.getShort() & 0xFFFF;
					definition.setMinimapFunction(minimapFunction == 0xFFFF ? -1 : minimapFunction);
				} else if (opcode == 62) {
					definition.setInverted(true);
				} else if (opcode == 64) {
					definition.setCastsShadow(false);
				} else if (opcode == 65) { // x
					definition.setScaleX(buffer.getShort() & 0xffff);
				} else if (opcode == 66) {
					definition.setScaleY(buffer.getShort() & 0xffff);
				} else if (opcode == 67) {
					definition.setScaleZ(buffer.getShort() & 0xffff);
				} else if (opcode == 68) {
					int mapscene = buffer.getShort() & 0xFFFF;
					definition.setMapscene(mapscene == 0xFFFF ? -1 : mapscene);
				} else if (opcode == 69) { // x
					definition.setSurroundings(buffer.get() & 0xff);
				} else if (opcode == 70) {
					definition.setTranslateX(buffer.getShort() << 2);
				} else if (opcode == 71) { // x
					definition.setTranslateY(buffer.getShort() << 2);
				} else if (opcode == 72) {
					definition.setTranslateZ(buffer.getShort() << 2);
				} else if (opcode == 73) { // x
					definition.setObstructsGround(true);
				} else if (opcode == 74) { // x
					definition.setHollow(true);
				} else if (opcode == 75) {
					definition.setSupportItems(buffer.get() & 0xff);
				} else if (opcode == 77 || opcode == 92) {
					int varbit = buffer.getShort() & 0xffff;
					if (varbit == 65535) {
						varbit = -1;
					}
					int varp = buffer.getShort() & 0xffff;
					if (varp == 65535) {
						varp = -1;
					}
					int var3 = -1;
					if (opcode == 92) {
						var3 = readSmart2Or4Null(buffer);
					}
					int count = buffer.get() & 0xff;
					int[] morphisms = new int[count + 2];
					for (int i = 0; i <= count; i++) {
						morphisms[i] = readSmart2Or4Null(buffer);
					}
					morphisms[count + 1] = var3;
					definition.setMorphisms(morphisms);
					definition.setVarbit(varbit);
					definition.setVarp(varp);
				} else if (opcode == 78) { // x
					buffer.getShort();
					buffer.get();
				} else if (opcode == 79) {
					buffer.getShort();
					buffer.getShort();
					buffer.get();
					int count = buffer.get() & 0xff;
					for (int index = 0; index < count; index++)
						buffer.getShort();
				} else if (opcode == 81) { // x
					definition.setContouredGround(true);
					buffer.get();
				} else if(opcode == 82) {
					// aBoolean3891 = true;
				} else if(opcode == 88) {
					//aBoolean3853 = false;
				} else if(opcode == 89) {
					//aBoolean3891 = true;
				} else if(opcode == 91) {
					//aBoolean3873 = true;
				} else if (opcode == 93) {
					definition.setContouredGround(true);
					buffer.getShort();
				} else if(opcode == 94) {
					definition.setContouredGround(true);
				} else if (opcode == 95) {
					definition.setContouredGround(true);
					buffer.getShort();
				} else if(opcode == 97) {
					// aBoolean3866 = true;
				} else if(opcode == 98) {
					//aBoolean3923 = true;
				} else if (opcode == 99) {
					buffer.get();
					buffer.getShort();
				} else if (opcode == 100) {
					buffer.get();
					buffer.getShort();
				} else if (opcode == 101) {
					buffer.get();
				} else if (opcode == 102) {
					int mapscene = buffer.getShort() & 0xFFFF;
					definition.setMapscene(mapscene == 0xFFFF ? -1 : mapscene);
				} else if(opcode == 103) {
					definition.setOccludes(false);
				} else if (opcode == 104) {
					buffer.get();
				} else if (opcode == 105) {
					// field6511 in 742 LocType
				} else if (opcode == 106) {
					int size = buffer.get() & 0xff;
					int fallbackAnimation = -1;
					for (int index = 0; index < size; index++) {
						int animationId = readSmart2Or4Null(buffer);
						if (animationId != -1 && fallbackAnimation == -1) {
							fallbackAnimation = animationId;
						}
						buffer.get();
					}
					if (fallbackAnimation != -1) {
						definition.setAnimation(fallbackAnimation);
					}
				} else if (opcode == 107) {
					int areaId = buffer.getShort() & 0xFFFF;
					definition.setAreaId(areaId == 0xFFFF ? -1 : areaId);
				} else if (opcode >= 150 && opcode < 155) {
					ByteBufferUtils.getOSRSString(buffer);
				} else if (opcode == 160) {
					int size = buffer.get() & 0xff;
					for (int index = 0; index < size; index++) {
						buffer.getShort();
					}
				} else if (opcode == 162) {
					definition.setContouredGround(true);
					buffer.getInt();
				} else if (opcode == 163) {
					buffer.get();
					buffer.get();
					buffer.get();
					buffer.get();
				} else if (opcode == 164) {
					buffer.getShort();
				} else if (opcode == 165) {
					buffer.getShort();
				} else if (opcode == 166) {
					buffer.getShort();
				} else if (opcode == 167) {
					buffer.getShort();
				} else if(opcode == 168) {
					//aBoolean3894 = true;
				} else if(opcode == 169) {
					//aBoolean3845 = true;
				} else if (opcode == 170) {
					ByteBufferUtils.getSmart(buffer);
				} else if (opcode == 171) {
					ByteBufferUtils.getSmart(buffer);
				} else if (opcode == 173) {
					buffer.getShort();
					buffer.getShort();
				} else if(opcode == 177) {
					// boolean ub = true;
				} else if (opcode == 178) {
					buffer.get();
				} else if (opcode == 189) {
					// field6472 in 742 LocType
				} else if (opcode >= 190 && opcode < 196) {
					buffer.getShort();
				} else if (opcode == 249) {
					int var1 = buffer.get() & 0xff;
					for (int var2 = 0; var2 < var1; var2++) {
						boolean b = (buffer.get() & 0xff) == 1;
						int var5 = ByteBufferUtils.readU24Int(buffer);
						if (b) {
							ByteBufferUtils.getOSRSString(buffer);
						} else {
							buffer.getInt();
						}
					}
				} else {
					log.debug("loc {} unknown opcode {} after {}", id, opcode, lastOpcode);
				}
				lastOpcode = opcode;
			}
		} catch (Exception ex) {
			log.error("Failed decoding loc {} at opcode {}", id, lastOpcode, ex);
		}

		if (!explicitInteractive) {
			boolean interactive = false;
			if (definition.getModelIds() != null) {
				int[] modelTypes = definition.getModelTypes();
				if (modelTypes == null) {
					interactive = true;
				} else {
					interactive = true;
					for (int modelType : modelTypes) {
						if (modelType != 10) {
							interactive = false;
							break;
						}
					}
				}
			}
			if (!interactive && definition.getInteractions() != null) {
				for (String action : definition.getInteractions()) {
					if (action != null) {
						interactive = true;
						break;
					}
				}
			}
			definition.setInteractive(interactive);
		}

		if (definition.isHollow()) {
			definition.setSolid(false);
			definition.setImpenetrable(false);
		}

		if (definition.getSupportItems() == -1) {
			definition.setSupportItems(definition.isSolid() ? 1 : 0);
		}
		return definition;
	}

	private int readSmart2Or4Null(ByteBuffer buffer) {
		if (buffer.get(buffer.position()) < 0) {
			return buffer.getInt() & Integer.MAX_VALUE;
		}
		int value = buffer.getShort() & 0xFFFF;
		return value == 0x7FFF ? -1 : value;
	}


	@Override
	public ObjectDefinition forId(int id) {
		ObjectDefinition definition = definitions.get(id);
		if (definition != null) {
			return definition;
		}
		ObjectDefinition missing = new ObjectDefinition();
		missing.reset();
		missing.setId(id);
		definitions.put(id, missing);
		return missing;
	}

	@Override
	public int count() {
		return definitions.size();
	}

	@Override
	public ObjectDefinition morphism(int id) {
		ObjectDefinition def = forId(id);
		if (def == null || def.getMorphisms() == null || def.getMorphisms().length == 0) {
			return null;
		}
		Client client = Client.getSingleton();
		if (client == null || client.settings == null) {
			return null;
		}
		int[] settings = client.settings;
		int morphismIndex = -1;
		if (def.getVarbit() != -1) {
			VariableBits bits = VariableBitLoader.lookup(def.getVarbit());
			if(bits == null){
				log.debug("varbit {} was null while resolving morphism for {}", def.getVarbit(), id);
				return null;
			}
			int variable = bits.getSetting();
			if (variable < 0 || variable >= settings.length) {
				return null;
			}
			int low = bits.getLow();
			int high = bits.getHigh();
			int bitCount = high - low;
			if (bitCount < 0 || bitCount >= Client.BIT_MASKS.length) {
				return null;
			}
			int mask = Client.BIT_MASKS[bitCount];
			morphismIndex = settings[variable] >> low & mask;
		} else if (def.getVarp() != -1) {
			int varp = def.getVarp();
			if (varp < 0 || varp >= settings.length) {
				return null;
			}
			morphismIndex = settings[varp];
		}
		int var2;
		if(morphismIndex >= 0 && morphismIndex < def.getMorphisms().length) {
			var2 = def.getMorphisms()[morphismIndex];
		} else {
			var2 = def.getMorphisms()[def.getMorphisms().length - 1];
		}
		return var2 == -1 ? null : ObjectDefinitionLoader.lookup(var2);
	}


}
