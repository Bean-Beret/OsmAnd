package net.osmand.binary;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import gnu.trove.map.hash.TLongObjectHashMap;
import net.osmand.PlatformUtil;
import net.osmand.binary.OsmandOdb.OsmAndHHRoutingIndex;
import net.osmand.binary.OsmandOdb.OsmAndHHRoutingIndex.HHRoutePointSegments;
import net.osmand.binary.OsmandOdb.OsmAndHHRoutingIndex.HHRoutePointSegments.Builder;
import net.osmand.data.LatLon;
import net.osmand.data.QuadRect;
import net.osmand.router.HHRouteDataStructure;
import net.osmand.router.HHRouteDataStructure.HHRouteRegionPointsCtx;
import net.osmand.router.HHRouteDataStructure.HHRoutingContext;
import net.osmand.router.HHRouteDataStructure.NetworkDBPoint;
import net.osmand.util.MapUtils;

public class BinaryHHRouteReaderAdapter {
	protected static final Log LOG = PlatformUtil.getLog(BinaryHHRouteReaderAdapter.class);

	public static class HHRouteBlockSegments {
		int idRangeStart;
		int idRangeLength;
		int profileId;
		int length;
		int filePointer;
		
		List<HHRouteBlockSegments> sublist;
	}
	
	
	public static class HHRoutePointsBox {
		int length;
		int filePointer;
		int left, right, bottom, top;

		public QuadRect getLatLonBox() {
			QuadRect q = new QuadRect();
			q.left = MapUtils.get31LongitudeX(left);
			q.right = MapUtils.get31LongitudeX(right);
			q.top = MapUtils.get31LatitudeY(top);
			q.bottom = MapUtils.get31LatitudeY(bottom);
			return q;
		}

		public boolean contains(LatLon e) {
			if(e == null) {
				return false;
			}
			int y = MapUtils.get31TileNumberY(e.getLatitude());
			int x = MapUtils.get31TileNumberX(e.getLongitude());
			return x >= left && x <= right && y >= top && y <= bottom;
		}
	}
	
	public static class HHRouteRegion extends BinaryIndexPart {
		public long edition;
		public String profile;
		public List<String> profileParams = new ArrayList<>();
		public HHRoutePointsBox top = null;
		/// not stored in cache
		public List<HHRouteBlockSegments> segments = null;

		@Override
		public String getPartName() {
			return "Highway routing";
		}

		@Override
		public int getFieldNumber() {
			return OsmandOdb.OsmAndStructure.HHROUTINGINDEX_FIELD_NUMBER;
		}

		public QuadRect getLatLonBbox() {
			if(top == null) {
				return new QuadRect();
			}
			return top.getLatLonBox();
		}
	}

	protected CodedInputStream codedIS;
	private final BinaryMapIndexReader map;

	protected BinaryHHRouteReaderAdapter(BinaryMapIndexReader map) {
		this.codedIS = map.codedIS;
		this.map = map;
	}

	protected void skipUnknownField(int t) throws IOException {
		map.skipUnknownField(t);
	}

	protected int readInt() throws IOException {
		return map.readInt();
	}
	
	public <T extends NetworkDBPoint> TLongObjectHashMap<T> initRegionAndLoadPoints(HHRouteRegion reg, short mapId, Class<T> cl) throws IOException {
		codedIS.seek(reg.filePointer);
		int oldLimit = codedIS.pushLimit(reg.length);
		TLongObjectHashMap<T> mp = new TLongObjectHashMap<>();
		reg.segments = new ArrayList<>();
		while (true) {
			int t = codedIS.readTag();
			int tag = WireFormat.getTagFieldNumber(t);
			switch (tag) {
			case 0:
				codedIS.popLimit(oldLimit);
				return mp;
			case OsmandOdb.OsmAndHHRoutingIndex.POINTBOXES_FIELD_NUMBER:
				readPointBox(cl, mapId, mp, null);
				break;
			case OsmandOdb.OsmAndHHRoutingIndex.POINTSEGMENTS_FIELD_NUMBER:
				reg.segments.add(readRegionSegmentHeader());
				break;
			default:
				skipUnknownField(t);
				break;
			}
		}
				
				
	}

	public void readHHIndex(HHRouteRegion region, boolean fullInit) throws IOException {
		region.profileParams.clear();
		while (true) {
			int t = codedIS.readTag();
			int tag = WireFormat.getTagFieldNumber(t);
			switch (tag) {
			case 0:
				return;
			case OsmandOdb.OsmAndHHRoutingIndex.EDITION_FIELD_NUMBER:
				region.edition = codedIS.readInt64();
				break;
			case OsmandOdb.OsmAndHHRoutingIndex.PROFILE_FIELD_NUMBER:
				region.profile = codedIS.readString();
				break;
			case OsmandOdb.OsmAndHHRoutingIndex.PROFILEPARAMS_FIELD_NUMBER:
				region.profileParams.add(codedIS.readString());
				break;
			case OsmandOdb.OsmAndHHRoutingIndex.POINTBOXES_FIELD_NUMBER:
				region.top = readPointBox(null, (short) 0, null, null);
				break;
			case OsmandOdb.OsmAndHHRoutingIndex.POINTSEGMENTS_FIELD_NUMBER:
				codedIS.skipRawBytes(codedIS.getBytesUntilLimit());
				break;
			default:
				skipUnknownField(t);
				break;
			}
		}
	}

	private <T extends NetworkDBPoint>  HHRoutePointsBox readPointBox(Class<T> cl, short mapId, 
			TLongObjectHashMap<T> mp, HHRoutePointsBox parent) throws IOException {
		HHRoutePointsBox box = new HHRoutePointsBox();
		box.length = readInt();
		box.filePointer = codedIS.getTotalBytesRead();
		int oldLimit = codedIS.pushLimit(box.length);
		while (true) {
			if (mp == null && box.bottom != 0 && box.top != 0 && box.right != 0 && box.left != 0) {
				codedIS.skipRawBytes(codedIS.getBytesUntilLimit());
			}
			int t = codedIS.readTag();
			int tag = WireFormat.getTagFieldNumber(t);
			switch (tag) {
			case 0:
				codedIS.popLimit(oldLimit);
				return box;
			case OsmAndHHRoutingIndex.HHRoutePointsBox.BOTTOM_FIELD_NUMBER:
				box.bottom = codedIS.readSInt32() +  (parent != null ? parent.bottom : 0);
				break;
			case OsmAndHHRoutingIndex.HHRoutePointsBox.TOP_FIELD_NUMBER:
				box.top = codedIS.readSInt32() +  (parent != null ? parent.top : 0);
				break;
			case OsmAndHHRoutingIndex.HHRoutePointsBox.RIGHT_FIELD_NUMBER:
				box.right = codedIS.readSInt32() +  (parent != null ? parent.right : 0);
				break;
			case OsmAndHHRoutingIndex.HHRoutePointsBox.LEFT_FIELD_NUMBER:
				box.left = codedIS.readSInt32() +  (parent != null ? parent.left : 0);
				break;
			case OsmAndHHRoutingIndex.HHRoutePointsBox.BOXES_FIELD_NUMBER:
				if (cl == null) {
					codedIS.skipRawBytes(codedIS.getBytesUntilLimit());
				} else {
					readPointBox(cl, mapId, mp, box);
				}
				break;
			case OsmAndHHRoutingIndex.HHRoutePointsBox.POINTS_FIELD_NUMBER:
				if (cl == null) {
					codedIS.skipRawBytes(codedIS.getBytesUntilLimit());
				} else {
					readPoint(cl, mapId, mp, box.left, box.top);
				}
				break;
			default:
				skipUnknownField(t);
				break;
			}
		}		
	}

	private <T extends NetworkDBPoint> T readPoint(Class<T> cl, short mapId, TLongObjectHashMap<T> mp, int dx, int dy) throws IOException {
		T pnt;
		try {
			pnt = cl.getDeclaredConstructor().newInstance();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}		
		pnt.mapId = mapId;
		int size = codedIS.readRawVarint32();
		int oldLimit = codedIS.pushLimit(size);
		int dualIdPoint = -1;
		while (true) {
			int t = codedIS.readTag();
			int tag = WireFormat.getTagFieldNumber(t);
			switch (tag) {
			case 0:
				codedIS.popLimit(oldLimit);
				mp.put(pnt.index, pnt);
				if (dualIdPoint >= 0 && mp.contains(dualIdPoint)) {
					pnt.dualPoint = mp.get(dualIdPoint);
					pnt.dualPoint.dualPoint = pnt;
					pnt.dualPoint.endX = pnt.startX;
					pnt.dualPoint.endY = pnt.startY;
					pnt.endX = pnt.dualPoint.startX;
					pnt.endY = pnt.dualPoint.startY;
				} 
				return pnt;
			case OsmAndHHRoutingIndex.HHRouteNetworkPoint.ID_FIELD_NUMBER:
				pnt.fileId = codedIS.readInt32();
				break;
			case OsmAndHHRoutingIndex.HHRouteNetworkPoint.DX_FIELD_NUMBER:
				pnt.endX = pnt.startX = (codedIS.readSInt32() + dx);
				break;
			case OsmAndHHRoutingIndex.HHRouteNetworkPoint.DY_FIELD_NUMBER:
				pnt.endY = pnt.startY = (codedIS.readSInt32() + dy);
				break;
			case OsmAndHHRoutingIndex.HHRouteNetworkPoint.GLOBALID_FIELD_NUMBER:
				pnt.index = codedIS.readInt32();
				break;
			case OsmAndHHRoutingIndex.HHRouteNetworkPoint.ROADID_FIELD_NUMBER:
				pnt.roadId = codedIS.readInt64();
				break;
			case OsmAndHHRoutingIndex.HHRouteNetworkPoint.ROADSTARTENDINDEX_FIELD_NUMBER:
				int v = codedIS.readInt32();
				pnt.start = (short) (v >> 1);
				pnt.end = (short) (pnt.start + (v % 2 == 1 ? 1 : -1));
				break;
			case OsmAndHHRoutingIndex.HHRouteNetworkPoint.CLUSTERID_FIELD_NUMBER:
				pnt.clusterId = codedIS.readInt32();
				break;
			case OsmAndHHRoutingIndex.HHRouteNetworkPoint.DUALPOINTID_FIELD_NUMBER:
				dualIdPoint = codedIS.readInt32();
				break;
			case OsmAndHHRoutingIndex.HHRouteNetworkPoint.DUALCLUSTERID_FIELD_NUMBER:
				codedIS.readInt32();
				break;
			default:
				skipUnknownField(t);
				break;
			}
		}
		
	}

	private HHRouteBlockSegments readRegionSegmentHeader() throws IOException {
		HHRouteBlockSegments block = new HHRouteBlockSegments();
		block.length = readInt();
		block.filePointer = codedIS.getTotalBytesRead();
		int oldLimit = codedIS.pushLimit(block.length);
		while (true) {
			int t = codedIS.readTag();
			int tag = WireFormat.getTagFieldNumber(t);
			switch (tag) {
			case 0:
				codedIS.popLimit(oldLimit);
				return block;
			case OsmAndHHRoutingIndex.HHRouteBlockSegments.IDRANGELENGTH_FIELD_NUMBER:
				block.idRangeLength = codedIS.readInt32();
				break;
			case OsmAndHHRoutingIndex.HHRouteBlockSegments.IDRANGESTART_FIELD_NUMBER:
				block.idRangeStart = codedIS.readInt32();
				break;
			case OsmAndHHRoutingIndex.HHRouteBlockSegments.PROFILEID_FIELD_NUMBER:
				block.profileId = codedIS.readInt32();
				break;
			case OsmAndHHRoutingIndex.HHRouteBlockSegments.INNERBLOCKS_FIELD_NUMBER:
				codedIS.skipRawBytes(codedIS.getBytesUntilLimit());
				break;
			case OsmAndHHRoutingIndex.HHRouteBlockSegments.POINTSEGMENTS_FIELD_NUMBER:
				codedIS.skipRawBytes(codedIS.getBytesUntilLimit());
				break;
			default:
				skipUnknownField(t);
				break;
			}
		}
		
	}
	
	private <T extends NetworkDBPoint> int loadNetworkSegmentPoint(HHRoutingContext<T>  ctx, HHRouteRegionPointsCtx<T> reg, 
			HHRouteBlockSegments block, int searchInd, boolean reverse) throws IOException {
		if (block.sublist != null) {
			for (HHRouteBlockSegments s : block.sublist) {
				if (checkId(searchInd, s)) {
					return loadNetworkSegmentPoint(ctx, reg, s, searchInd, reverse);
				}
			}
			return 0;
		}
		if (codedIS.getTotalBytesRead() != block.filePointer) {
			codedIS.seek(block.filePointer);
		}
		int loaded = 0;
		int oldLimit = codedIS.pushLimit(block.length);
		int ind = 0;
		try {
			while (true) {
				int t = codedIS.readTag();
				int tag = WireFormat.getTagFieldNumber(t);
				switch (tag) {
				case 0:
                    // codedIS.popLimit(oldLimit); // finally
					return loaded;
				case OsmAndHHRoutingIndex.HHRouteBlockSegments.IDRANGELENGTH_FIELD_NUMBER:
					block.idRangeLength = codedIS.readInt32();
					break;
				case OsmAndHHRoutingIndex.HHRouteBlockSegments.IDRANGESTART_FIELD_NUMBER:
					block.idRangeStart = codedIS.readInt32();
					break;
				case OsmAndHHRoutingIndex.HHRouteBlockSegments.PROFILEID_FIELD_NUMBER:
					block.profileId = codedIS.readInt32();
					break;
				case OsmAndHHRoutingIndex.HHRouteBlockSegments.INNERBLOCKS_FIELD_NUMBER:
					if (!checkId(searchInd, block)) {
						codedIS.skipRawBytes(codedIS.getBytesUntilLimit());
					} else {
						// read all sublist
						if (block.sublist == null) {
							block.sublist = new ArrayList<>();
						}
						HHRouteBlockSegments child = new HHRouteBlockSegments();
						child.length = readInt();
						child.filePointer = codedIS.getTotalBytesRead();
						int olLimit = codedIS.pushLimit(child.length);
						loaded += loadNetworkSegmentPoint(ctx, reg, child, searchInd, reverse);
						codedIS.popLimit(olLimit);
						block.sublist.add(child);
					}
					break;
				case OsmAndHHRoutingIndex.HHRouteBlockSegments.POINTSEGMENTS_FIELD_NUMBER:
					if (!checkId(searchInd, block)) {
						codedIS.skipRawBytes(codedIS.getBytesUntilLimit());
					} else {
						int pntFileId = (ind++) + block.idRangeStart;
						T point = reg.getPoint(pntFileId);
						Builder bld = HHRoutePointSegments.newBuilder();
						codedIS.readMessage(bld, null);
						HHRoutePointSegments s = bld.buildPartial();
						if (point != null) {
							// not used from this file
							HHRouteDataStructure.setSegments(ctx, point, s.getSegmentsIn().toByteArray(),
									s.getSegmentsOut().toByteArray());
							loaded += point.connected(true).size() + point.connected(false).size();
						}
					}

					break;
				default:
					skipUnknownField(t);
					break;
				}
			}
		} finally {
			codedIS.popLimit(oldLimit);
		}
	}

	private <T extends NetworkDBPoint> boolean checkId(int id, HHRouteBlockSegments s) {
		return s.idRangeStart <= id && s.idRangeStart + s.idRangeLength > id;
	}

	public <T extends NetworkDBPoint> int loadNetworkSegmentPoint(HHRoutingContext<T> ctx,
			HHRouteRegionPointsCtx<T> reg, T point, boolean reverse) throws IOException {
		if (point.connected(reverse) != null) {
			return 0;
		}
		HHRouteRegion fileRegion = reg.getFileRegion();
		for (HHRouteBlockSegments s : fileRegion.segments) {
			if (s.profileId == reg.getRoutingProfile() && checkId(point.fileId, s)) {
				return loadNetworkSegmentPoint(ctx, reg, s, point.fileId, reverse);
			}
		}
		return 0;
	}
	
	

}
