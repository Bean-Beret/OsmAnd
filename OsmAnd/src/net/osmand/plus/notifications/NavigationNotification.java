package net.osmand.plus.notifications;

import static net.osmand.plus.NavigationService.DEEP_LINK_ACTION_OPEN_ROOT_SCREEN;
import static net.osmand.plus.NavigationService.USED_BY_NAVIGATION;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

import androidx.car.app.notification.CarAppExtender;
import androidx.car.app.notification.CarPendingIntent;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.BigTextStyle;
import androidx.core.app.NotificationCompat.Builder;

import net.osmand.Location;
import net.osmand.plus.NavigationService;
import net.osmand.plus.utils.OsmAndFormatter;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.helpers.TargetPointsHelper.TargetPoint;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.auto.NavigationCarAppService;
import net.osmand.plus.auto.NavigationSession;
import net.osmand.plus.routing.RouteCalculationResult;
import net.osmand.plus.routing.RouteCalculationResult.NextDirectionInfo;
import net.osmand.plus.routing.RouteDirectionInfo;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.views.mapwidgets.TurnDrawable;
import net.osmand.router.TurnType;
import net.osmand.util.Algorithms;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class NavigationNotification extends OsmandNotification {

	public static final String OSMAND_PAUSE_NAVIGATION_SERVICE_ACTION = "OSMAND_PAUSE_NAVIGATION_SERVICE_ACTION";
	public static final String OSMAND_RESUME_NAVIGATION_SERVICE_ACTION = "OSMAND_RESUME_NAVIGATION_SERVICE_ACTION";
	public static final String OSMAND_STOP_NAVIGATION_SERVICE_ACTION = "OSMAND_STOP_NAVIGATION_SERVICE_ACTION";
	public static final String GROUP_NAME = "NAVIGATION";

	private boolean leftSide;

	public NavigationNotification(OsmandApplication app) {
		super(app, GROUP_NAME);
	}

	@Override
	public void init() {
		leftSide = app.getSettings().DRIVING_REGION.get().leftHandDriving;
		app.registerReceiver(new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				RoutingHelper routingHelper = app.getRoutingHelper();
				routingHelper.setRoutePlanningMode(true);
				routingHelper.setFollowingMode(false);
				routingHelper.setPauseNavigation(true);
			}
		}, new IntentFilter(OSMAND_PAUSE_NAVIGATION_SERVICE_ACTION));

		app.registerReceiver(new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				RoutingHelper routingHelper = app.getRoutingHelper();
				routingHelper.setRoutePlanningMode(false);
				routingHelper.setFollowingMode(true);
				routingHelper.setCurrentLocation(getLastKnownLocation(), false);
			}
		}, new IntentFilter(OSMAND_RESUME_NAVIGATION_SERVICE_ACTION));

		app.registerReceiver(new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				app.stopNavigation();
			}
		}, new IntentFilter(OSMAND_STOP_NAVIGATION_SERVICE_ACTION));
	}

	@Override
	public NotificationType getType() {
		return NotificationType.NAVIGATION;
	}

	@Override
	public int getPriority() {
		return NotificationCompat.PRIORITY_HIGH;
	}

	@Override
	public boolean isActive() {
		NavigationService service = app.getNavigationService();
		return isEnabled()
				&& service != null
				&& (service.getUsedBy() & USED_BY_NAVIGATION) != 0;
	}

	@Override
	public boolean isEnabled() {
		RoutingHelper routingHelper = app.getRoutingHelper();
		return routingHelper.isFollowingMode()
				|| (routingHelper.isRoutePlanningMode() && routingHelper.isPauseNavigation());
	}

	@Override
	public Intent getContentIntent() {
		return new Intent(app, MapActivity.class);
	}

	@Override
	public Builder buildNotification(boolean wearable) {
		if (!isEnabled()) {
			return null;
		}
		NavigationService service = app.getNavigationService();
		String notificationTitle;
		StringBuilder notificationText = new StringBuilder();
		color = 0;
		icon = R.drawable.ic_notification_start_navigation;
		Bitmap turnBitmap = null;
		ongoing = true;
		RoutingHelper routingHelper = app.getRoutingHelper();
		if (service != null && (service.getUsedBy() & USED_BY_NAVIGATION) != 0) {
			color = app.getColor(R.color.osmand_orange);

			String distanceStr = OsmAndFormatter.getFormattedDistance(routingHelper.getLeftDistance(), app);
			String timeStr = OsmAndFormatter.getFormattedDuration(routingHelper.getLeftTime(), app);
			String etaStr = SimpleDateFormat.getTimeInstance(DateFormat.SHORT)
					.format(new Date(System.currentTimeMillis() + routingHelper.getLeftTime() * 1000L));
			String speedStr = null;
			Location location = getLastKnownLocation();
			if (location != null && location.hasSpeed()) {
				speedStr = OsmAndFormatter.getFormattedSpeed(location.getSpeed(), app);
			}

			TurnType turnType = null;
			boolean deviatedFromRoute;
			int turnImminent = 0;
			int nextTurnDistance = 0;
			int nextNextTurnDistance = 0;
			String nextNextTurnDescription = "";
			String nextTurnDescription = "";
			RouteDirectionInfo ri = null;
			if (routingHelper.isRouteCalculated() && routingHelper.isFollowingMode()) {
				deviatedFromRoute = routingHelper.isDeviatedFromRoute();

				if (deviatedFromRoute) {
					turnImminent = 0;
					turnType = TurnType.valueOf(TurnType.OFFR, leftSide);
					nextTurnDistance = (int) routingHelper.getRouteDeviation();
				} else {
					NextDirectionInfo calc1 = new NextDirectionInfo();
					NextDirectionInfo r = routingHelper.getNextRouteDirectionInfo(calc1, true);
					if (r != null && r.distanceTo > 0 && r.directionInfo != null) {
						ri = r.directionInfo;
						turnType = r.directionInfo.getTurnType();
						nextTurnDistance = r.distanceTo;
						turnImminent = r.imminent;
						nextTurnDescription = r.directionInfo.getDescriptionRoutePart();
						NextDirectionInfo next = routingHelper.getNextRouteDirectionInfoAfter(r, calc1, true);
						nextNextTurnDistance = next.distanceTo;
						nextNextTurnDescription = next.directionInfo.getDescriptionRoutePart();
					}
				}

				if (turnType != null) {
					TurnDrawable drawable = new TurnDrawable(app, false);
					int height = (int) app.getResources().getDimension(android.R.dimen.notification_large_icon_height);
					int width = (int) app.getResources().getDimension(android.R.dimen.notification_large_icon_width);
					drawable.setBounds(0, 0, width, height);
					drawable.setTurnType(turnType);
					drawable.setTurnImminent(turnImminent, deviatedFromRoute);
					turnBitmap = drawableToBitmap(drawable);
				}

				String[] nextTurnTypeSplit = {nextTurnDescription};
				if (nextTurnDescription.length() > 0) {
					nextTurnTypeSplit = nextTurnDescription.split(" ", 2);
				}

				notificationTitle = OsmAndFormatter.getFormattedDistance(nextTurnDistance, app).replace(" ", "") + " " + nextTurnTypeSplit[0];
						// + (turnType != null ? " " + RouteCalculationResult.toString(turnType, app, true) :
				if (nextTurnTypeSplit.length > 1) {
					notificationText.append(nextTurnTypeSplit[1]).append(" ");
				}
				if (ri != null && !Algorithms.isEmpty(ri.getDescriptionRoutePart())) {
					// notificationText.append(ri.getDescriptionRoutePart());
					if (nextNextTurnDistance > 0) {
						notificationText.append(" ").append(OsmAndFormatter.getFormattedDistance(nextNextTurnDistance, app).replace(" ", ""))
								.append(nextNextTurnDescription != null ? " " + nextNextTurnDescription : "");
					}
					notificationText.append("\n");
				}

				int distanceToNextIntermediate = routingHelper.getLeftDistanceNextIntermediate();
				if (distanceToNextIntermediate > 0) {
					int nextIntermediateIndex = routingHelper.getRoute().getNextIntermediate();
					List<TargetPoint> intermediatePoints = app.getTargetPointsHelper().getIntermediatePoints();
					if (nextIntermediateIndex < intermediatePoints.size()) {
						TargetPoint nextIntermediate = intermediatePoints.get(nextIntermediateIndex);
						notificationText.append(OsmAndFormatter.getFormattedDistance(distanceToNextIntermediate, app))
								.append(" ")
								.append(nextIntermediate.getOnlyName());
						notificationText.append("\n");
					}
				}
				notificationText.append(distanceStr)
						.append(" ").append(timeStr)
						.append(" ").append(etaStr);
				if (speedStr != null) {
					notificationText.append(" ").append(speedStr);
				}
			} else {
				notificationTitle = app.getString(R.string.shared_string_navigation);
				String error = routingHelper.getLastRouteCalcErrorShort();
				if (Algorithms.isEmpty(error)) {
					notificationText.append(app.getString(R.string.route_calculation)).append("...");
				} else {
					notificationText.append(error);
				}
			}
		} else if (routingHelper.isRoutePlanningMode() && routingHelper.isPauseNavigation()) {
			ongoing = false;
			notificationTitle = app.getString(R.string.shared_string_navigation);
			notificationText.append(app.getString(R.string.shared_string_paused));
		} else {
			return null;
		}

		Builder notificationBuilder = createBuilder(wearable)
				.setContentTitle(notificationTitle)
				.setCategory(NotificationCompat.CATEGORY_NAVIGATION)
				.setContentText(notificationText)
				// .setStyle(new BigTextStyle().bigText(notificationText))
				.setLargeIcon(turnBitmap);

		NavigationSession carNavigationSession = app.getCarNavigationSession();
		if (carNavigationSession != null) {
			Intent intent = new Intent(Intent.ACTION_VIEW)
					.setComponent(new ComponentName(app, NavigationCarAppService.class))
					.setData(NavigationCarAppService.createDeepLinkUri(DEEP_LINK_ACTION_OPEN_ROOT_SCREEN));
			notificationBuilder.extend(
					new CarAppExtender.Builder()
							//.setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
							.setContentIntent(CarPendingIntent.getCarApp(app, intent.hashCode(), intent, 0))
							.build());
		}

		Intent stopIntent = new Intent(OSMAND_STOP_NAVIGATION_SERVICE_ACTION);
		PendingIntent stopPendingIntent = PendingIntent.getBroadcast(app, 0, stopIntent,
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
		notificationBuilder.addAction(R.drawable.ic_notification_remove,
				app.getString(R.string.shared_string_control_stop), stopPendingIntent);

		if (routingHelper.isRouteCalculated() && routingHelper.isFollowingMode()) {
			Intent pauseIntent = new Intent(OSMAND_PAUSE_NAVIGATION_SERVICE_ACTION);
			PendingIntent pausePendingIntent = PendingIntent.getBroadcast(app, 0, pauseIntent,
					PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
			notificationBuilder.addAction(R.drawable.ic_notification_pause,
					app.getString(R.string.shared_string_pause), pausePendingIntent);
		} else if (routingHelper.isRouteCalculated() && routingHelper.isPauseNavigation()) {
			Intent resumeIntent = new Intent(OSMAND_RESUME_NAVIGATION_SERVICE_ACTION);
			PendingIntent resumePendingIntent = PendingIntent.getBroadcast(app, 0, resumeIntent,
					PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
			notificationBuilder.addAction(R.drawable.ic_notification_play,
					app.getString(R.string.shared_string_resume), resumePendingIntent);
		}

		return notificationBuilder;
	}

	@Override
	public void setupNotification(Notification notification) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			int smallIconViewId = app.getResources().getIdentifier("right_icon", "id", android.R.class.getPackage().getName());

			if (smallIconViewId != 0) {
				if (notification.contentView != null)
					notification.contentView.setViewVisibility(smallIconViewId, View.INVISIBLE);

				if (notification.headsUpContentView != null)
					notification.headsUpContentView.setViewVisibility(smallIconViewId, View.INVISIBLE);

				if (notification.bigContentView != null)
					notification.bigContentView.setViewVisibility(smallIconViewId, View.INVISIBLE);
			}
		}
	}

	private Location getLastKnownLocation() {
		return app.getLocationProvider().getLastKnownLocation();
	}

	public Bitmap drawableToBitmap(Drawable drawable) {
		int height = (int) app.getResources().getDimension(android.R.dimen.notification_large_icon_height);
		int width = (int) app.getResources().getDimension(android.R.dimen.notification_large_icon_width);

		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		canvas.drawColor(0, PorterDuff.Mode.CLEAR);
		drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
		drawable.draw(canvas);

		return bitmap;
	}

	@Override
	public int getOsmandNotificationId() {
		return NAVIGATION_NOTIFICATION_SERVICE_ID;
	}

	@Override
	public int getOsmandWearableNotificationId() {
		return WEAR_NAVIGATION_NOTIFICATION_SERVICE_ID;
	}
}
