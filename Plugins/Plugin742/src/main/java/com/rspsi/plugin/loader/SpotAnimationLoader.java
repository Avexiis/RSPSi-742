package com.rspsi.plugin.loader;

import com.jagex.cache.anim.Graphic;
import com.jagex.cache.loader.anim.AnimationDefinitionLoader;
import com.jagex.cache.loader.anim.GraphicLoader;
import com.jagex.io.Buffer;
import org.displee.cache.index.Index;
import org.displee.cache.index.archive.Archive;
import org.displee.cache.index.archive.file.File;

public class SpotAnimationLoader extends GraphicLoader {

	private Graphic[] graphics;
	private int count;

	@Override
	public int count() {
		return count;
	}

	@Override
	public Graphic forId(int id) {
		if (graphics == null || id < 0 || id >= graphics.length) {
			return null;
		}
		return graphics[id];
	}

	@Override
	public void init(Archive archive) {
		if (archive == null) {
			graphics = new Graphic[0];
			count = 0;
			return;
		}
		graphics = new Graphic[archive.getHighestId() + 1];
		for (File file : archive.getFiles()) {
			if (file != null && file.getData() != null) {
				graphics[file.getId()] = decode(new Buffer(file.getData()));
			}
		}
		count = graphics.length;
	}

	@Override
	public void init(byte[] data) {
		graphics = new Graphic[0];
		count = 0;
	}

	public void decodeGraphics(Index index) {
		int size = index.getLastArchive().getId() * 256 + index.getLastArchive().getLastFile().getId() + 1;
		graphics = new Graphic[size];
		count = size;
	}

	public Graphic decode(Buffer buffer) {
		Graphic graphic = new Graphic();
		int lastOpcode = -1;
		do {
			int opcode = buffer.readUByte();
			if (opcode == 0) {
				return graphic;
			}

			if (opcode == 1) {
				graphic.setModel(readSmart2Or4Null(buffer));
			} else if (opcode == 2) {
				int animationId = readSmart2Or4Null(buffer);
				if (animationId >= 0) {
					graphic.setAnimation(AnimationDefinitionLoader.getAnimation(animationId));
				}
				graphic.setAnimationId(animationId);
			} else if (opcode == 4) {
				graphic.setBreadthScale(buffer.readUShort());
			} else if (opcode == 5) {
				graphic.setDepthScale(buffer.readUShort());
			} else if (opcode == 6) {
				graphic.setOrientation(buffer.readUShort());
			} else if (opcode == 7) {
				graphic.setAmbience(buffer.readUByte());
			} else if (opcode == 8) {
				graphic.setModelShadow(buffer.readUByte());
			} else if (opcode == 9 || opcode == 10 || opcode == 11 || opcode == 12 || opcode == 13) {
				// Unsupported EffectAnimType field in this client model.
			} else if (opcode == 14) {
				buffer.readUByte();
			} else if (opcode == 15) {
				buffer.readUShort();
			} else if (opcode == 16) {
				buffer.readInt();
			} else if (opcode == 40) {
				int len = buffer.readUByte();
				int[] originalColours = new int[len];
				int[] replacementColours = new int[len];
				for (int i = 0; i < len; i++) {
					originalColours[i] = buffer.readUShort();
					replacementColours[i] = buffer.readUShort();
				}
				graphic.setOriginalColours(originalColours);
				graphic.setReplacementColours(replacementColours);
			} else if (opcode == 41) {
				int len = buffer.readUByte();
				for (int i = 0; i < len; i++) {
					buffer.readUShort();
					buffer.readUShort();
				}
			} else if (opcode == 44 || opcode == 45) {
				buffer.readUShort();
			} else {
				System.out.println("Error unrecognised spotanim config code: " + opcode + " last: " + lastOpcode);
			}
			lastOpcode = opcode;
		} while (true);
	}

	private int readSmart2Or4Null(Buffer buffer) {
		int peek = buffer.getPayload()[buffer.getPosition()];
		if (peek < 0) {
			return buffer.readInt() & Integer.MAX_VALUE;
		}
		int value = buffer.readUShort();
		return value == 0x7FFF ? -1 : value;
	}

}
