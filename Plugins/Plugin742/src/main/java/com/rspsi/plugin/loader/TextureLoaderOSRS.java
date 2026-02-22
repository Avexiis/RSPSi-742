package com.rspsi.plugin.loader;

import com.jagex.cache.graphics.Sprite;
import com.jagex.cache.loader.textures.TextureLoader;
import com.jagex.draw.textures.SpriteTexture;
import com.jagex.draw.textures.Texture;
import lombok.extern.slf4j.Slf4j;
import org.displee.cache.index.Index;
import com.rspsi.misc.FixedHashMap;
import org.displee.cache.index.archive.Archive;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

@Slf4j
public class TextureLoaderOSRS extends TextureLoader {

	private static final int DEFAULT_CAPACITY = 0;
	private Texture[] textures = new Texture[DEFAULT_CAPACITY];
	private boolean[] transparent = new boolean[DEFAULT_CAPACITY];
	private double brightness = 0.8;
	private FixedHashMap<Integer, int[]> textureCache = new FixedHashMap<Integer, int[]>(20);
	private Index textureIndex;
	private Index spriteIndex;
	
	
	@Override
	public Texture forId(int arg0) {
		if(arg0 < 0 || arg0 >= textures.length)
			return null;
		Texture texture = textures[arg0];
		if (texture != null) {
			return texture;
		}
		texture = decodeTexture(arg0);
		if (texture != null) {
			texture.setBrightness(brightness);
			textures[arg0] = texture;
			transparent[arg0] = texture.supportsAlpha();
		}
		return texture;
	}

	@Override
	public int[] getPixels(int textureId) {

		if (textureCache.contains(textureId)) {
			return textureCache.get(textureId);
		}
		Texture texture = forId(textureId);
		if(texture == null) {
			return null;
		}
		int[] texels = texture.getPixels();
		if (texels == null) {
			return null;
		}
		textureCache.put(textureId, texels);
		return texels;
	}

	@Override
	public void init(Archive archive) {
		// Unused in this plugin; textures are initialized via index-based init.
	}

	@Override
	public boolean isTransparent(int arg0) {
		if (arg0 < 0 || arg0 >= transparent.length) {
			return false;
		}
		if (textures[arg0] == null) {
			forId(arg0);
		}
		return transparent[arg0];
	}

	@Override
	public void setBrightness(double arg0) {
		textureCache.clear();
		this.brightness = arg0;
		for (Texture texture : textures) {
			if (texture != null) {
				texture.setBrightness(brightness);
			}
		}
	}

	@Override
	public int count() {
		return textures.length;
	}

	@Override
	public void init(byte[] arg0) {
		
	}

	public void init(Index textureIndex, Index spriteIndex) {
		this.textureIndex = textureIndex;
		this.spriteIndex = spriteIndex;
		textureCache.clear();
		int textureSlots = lastArchiveId(textureIndex);
		int spriteSlots = lastArchiveId(spriteIndex);
		int capacity = Math.max(textureSlots, spriteSlots);
		if (capacity < 0) {
			capacity = 0;
		}
		this.textures = new Texture[capacity];
		this.transparent = new boolean[capacity];
		log.info("Initialized texture loader with {} slots (texture index={}, sprite index={})", capacity, textureSlots, spriteSlots);
	}

	private int lastArchiveId(Index index) {
		if (index == null || index.getLastArchive() == null) {
			return 0;
		}
		return index.getLastArchive().getId() + 1;
	}

	private Texture decodeTexture(int id) {
		Texture texture = decodeFromIndex(textureIndex, id, true);
		if (texture != null) {
			return texture;
		}
		return decodeFromIndex(spriteIndex, id, true);
	}

	private Texture decodeFromIndex(Index index, int id, boolean allowImageDecode) {
		if (index == null) {
			return null;
		}
		Archive archive = index.getArchive(id);
		if (archive == null || archive.getFiles() == null) {
			return null;
		}
		byte[] data = null;
		for (org.displee.cache.index.archive.file.File file : archive.getFiles()) {
			if (file != null && file.getData() != null && file.getData().length > 0) {
				data = file.getData();
				break;
			}
		}
		if (data == null) {
			return null;
		}
		Texture texture = decodePackedSprite(data);
		if (texture != null) {
			return texture;
		}
		if (allowImageDecode) {
			return decodeImageTexture(data);
		}
		return null;
	}

	private Texture decodePackedSprite(byte[] data) {
		try {
			Sprite sprite = Sprite.decode(ByteBuffer.wrap(data));
			if (sprite != null) {
				return new SpriteTexture(sprite);
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private Texture decodeImageTexture(byte[] data) {
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
			if (image == null) {
				return null;
			}
			int width = image.getWidth();
			int height = image.getHeight();
			if (width <= 0 || height <= 0) {
				return null;
			}
			int[] pixels = new int[width * height];
			image.getRGB(0, 0, width, height, pixels, 0, width);
			boolean hasAlpha = false;
			for (int pixel : pixels) {
				if ((pixel >>> 24) != 0xFF) {
					hasAlpha = true;
					break;
				}
			}
			return new RawTexture(width, height, pixels, hasAlpha);
		} catch (Exception ignored) {
		}
		return null;
	}

	private static final class RawTexture extends Texture {
		private final boolean hasAlpha;

		private RawTexture(int width, int height, int[] pixels, boolean hasAlpha) {
			super(width, height);
			this.originalPixels = Arrays.copyOf(pixels, pixels.length);
			this.pixels = Arrays.copyOf(pixels, pixels.length);
			this.hasAlpha = hasAlpha;
			generatePalette();
		}

		@Override
		public boolean supportsAlpha() {
			return hasAlpha;
		}
	}

}
