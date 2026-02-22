package com.rspsi.plugin.loader;

import com.jagex.cache.anim.Animation;
import com.jagex.cache.loader.anim.AnimationDefinitionLoader;
import com.jagex.io.Buffer;
import org.displee.cache.index.archive.Archive;
import org.displee.cache.index.archive.file.File;

import java.util.Arrays;

public class AnimationDefLoader extends AnimationDefinitionLoader {

	private int count;
	private Animation[] animations;

	@Override
	public void init(Archive archive) {
		if (archive == null) {
			animations = new Animation[0];
			count = 0;
			return;
		}
		animations = new Animation[archive.getHighestId() + 1];
		for (File file : archive.getFiles()) {
			if (file != null && file.getData() != null) {
				animations[file.getId()] = decode(new Buffer(file.getData()));
			}
		}
		for (int i = 0; i < animations.length; i++) {
			if (animations[i] == null) {
				animations[i] = createDefaultAnimation();
			}
		}
		count = animations.length;
	}

	@Override
	public void init(byte[] data) {
		animations = new Animation[0];
		count = 0;
	}

	protected Animation decode(Buffer buffer) {
		Animation animation = new Animation();
		do {
			int opcode = buffer.readUByte();
			if (opcode == 0) {
				break;
			}
			if (opcode == 1) {
				int frameCount = buffer.readUShort();
				int[] durations = new int[frameCount];
				for (int frame = 0; frame < frameCount; frame++) {
					durations[frame] = buffer.readUShort();
				}

				int[] primaryFrames = new int[frameCount];
				for (int frame = 0; frame < frameCount; frame++) {
					primaryFrames[frame] = buffer.readUShort();
				}
				for (int frame = 0; frame < frameCount; frame++) {
					primaryFrames[frame] += buffer.readUShort() << 16;
				}

				int[] secondaryFrames = new int[frameCount];
				Arrays.fill(secondaryFrames, -1);

				animation.setFrameCount(frameCount);
				animation.setPrimaryFrames(primaryFrames);
				animation.setSecondaryFrames(secondaryFrames);
				animation.setDurations(durations);
			} else if (opcode == 2) {
				animation.setLoopOffset(buffer.readUShort());
			} else if (opcode == 3) {
				int count = buffer.readUByte();
				int[] interleaveOrder = new int[count + 1];
				for (int index = 0; index < count; index++) {
					interleaveOrder[index] = buffer.readUByte();
				}
				interleaveOrder[count] = 9999999;
				animation.setInterleaveOrder(interleaveOrder);
			} else if (opcode == 5) {
				animation.setPriority(buffer.readUByte());
			} else if (opcode == 6) {
				animation.setPlayerOffhand(buffer.readUShort());
			} else if (opcode == 7) {
				animation.setPlayerMainhand(buffer.readUShort());
			} else if (opcode == 8) {
				animation.setMaximumLoops(buffer.readUByte());
			} else if (opcode == 9) {
				animation.setAnimatingPrecedence(buffer.readUByte());
			} else if (opcode == 10) {
				animation.setWalkingPrecedence(buffer.readUByte());
			} else if (opcode == 11) {
				animation.setReplayMode(buffer.readUByte());
			} else if (opcode == 12) {
				int len = buffer.readUByte();
				for (int i = 0; i < len; i++) {
					buffer.readUShort();
				}
				for (int i = 0; i < len; i++) {
					buffer.readUShort();
				}
			} else if (opcode == 13) {
				int len = buffer.readUShort();
				for (int i = 0; i < len; i++) {
					int nested = buffer.readUByte();
					if (nested > 0) {
						buffer.readUTriByte();
						for (int j = 1; j < nested; j++) {
							buffer.readUShort();
						}
					}
				}
			} else if (opcode == 14) {
				animation.setStretches(true);
			} else if (opcode == 15 || opcode == 16 || opcode == 18) {
				// Unsupported SeqType flag in this client model.
			} else if (opcode == 19) {
				buffer.readUByte();
				buffer.readUByte();
			} else if (opcode == 20) {
				buffer.readUByte();
				buffer.readUShort();
				buffer.readUShort();
			} else if (opcode == 22) {
				buffer.readUByte();
			} else if (opcode == 249) {
				int len = buffer.readUByte();
				for (int i = 0; i < len; i++) {
					boolean isString = buffer.readUByte() == 1;
					buffer.readUTriByte();
					if (isString) {
						buffer.readOSRSString();
					} else {
						buffer.readInt();
					}
				}
			} else {
				System.out.println("Error unrecognised seq config code: " + opcode);
			}
		} while (true);

		if (animation.getFrameCount() == 0) {
			animation = createDefaultAnimation();
		}

		if (animation.getAnimatingPrecedence() == -1) {
			animation.setAnimatingPrecedence(animation.getInterleaveOrder() == null ? 0 : 2);
		}

		if (animation.getWalkingPrecedence() == -1) {
			animation.setWalkingPrecedence(animation.getInterleaveOrder() == null ? 0 : 2);
		}
		return animation;
	}

	private Animation createDefaultAnimation() {
		Animation animation = new Animation();
		animation.setFrameCount(1);
		animation.setPrimaryFrames(new int[]{-1});
		animation.setSecondaryFrames(new int[]{-1});
		animation.setDurations(new int[]{-1});
		return animation;
	}

	@Override
	public int count() {
		return count;
	}

	@Override
	public Animation forId(int id) {
		if (animations == null || animations.length == 0) {
			return createDefaultAnimation();
		}
		if (id < 0 || id >= animations.length)
			id = 0;
		return animations[id];
	}

}
