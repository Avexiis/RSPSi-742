package com.jagex.entity.model;

public class MeshUtils {

	public static MeshRevision getRevision(byte[] data) {
		if (data[data.length - 1] == -1 && data[data.length - 2] == -1) {
			int flags = data[data.length - 18] & 0xff;
			boolean revision622OrHigher = (0x8 & flags) == 8;
			if (revision622OrHigher && data.length > 24) {
				int format = data[data.length - 24] & 0xff;
				if (format >= 15) {
					return MeshRevision.REVISION_742;
				}
			}
			return revision622OrHigher ? MeshRevision.REVISION_622 : MeshRevision.REVISION_525;
		}
		return MeshRevision.REVISION_317;
	}

}
