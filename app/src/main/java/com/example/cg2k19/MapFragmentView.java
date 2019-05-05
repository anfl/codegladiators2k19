package com.example.cg2k19;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.common.GeoPosition;
import com.here.android.mpa.common.Image;
import com.here.android.mpa.common.LocationDataSourceHERE;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.common.PositioningManager;
import com.here.android.mpa.guidance.NavigationManager;
import com.here.android.mpa.mapping.LocalMesh;
import com.here.android.mpa.mapping.Map;
import com.here.android.mpa.mapping.MapFragment;
import com.here.android.mpa.mapping.MapMarker;
import com.here.android.mpa.routing.CoreRouter;
import com.here.android.mpa.routing.Route;
import com.here.android.mpa.routing.RouteOptions;
import com.here.android.mpa.routing.RoutePlan;
import com.here.android.mpa.routing.RouteResult;
import com.here.android.mpa.routing.RouteWaypoint;
import com.here.android.mpa.routing.RoutingError;


import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * This class encapsulates the properties and functionality of the Map view.
 */
public class MapFragmentView {
    private MapFragment m_mapFragment;
    private AppCompatActivity m_activity;
    private Map m_map;
    private Context contextl;
    private MapMarker m_positionIndicatorFixed = null;
    private PointF m_mapTransformCenter;
    private boolean m_returningToRoadViewMode = false;
    private double m_lastZoomLevelInRoadViewMode = 0.0;

    public MapFragmentView(AppCompatActivity activity, Context context) {
        m_activity = activity;
        initMapFragment();
        this.contextl = context;
    }

    private MapFragment getMapFragment() {
        return (MapFragment) m_activity.getFragmentManager().findFragmentById(R.id.mapfragment);
    }

    private void initMapFragment() {
        /* Locate the mapFragment UI element */
        m_mapFragment = getMapFragment();

        // Set path of isolated disk cache
        String diskCacheRoot = Environment.getExternalStorageDirectory().getPath()
                + File.separator + ".isolated-here-maps";
        // Retrieve intent name from manifest
        String intentName = "";
        try {
            ApplicationInfo ai = m_activity.getPackageManager().getApplicationInfo(m_activity.getPackageName(),
                    PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            intentName = bundle.getString("test");
            Toast.makeText(m_activity, "try", Toast.LENGTH_SHORT).show();
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(m_activity, "catch", Toast.LENGTH_SHORT).show();
            Log.e(this.getClass().toString(), "Failed to find intent name, NameNotFound: " + e.getMessage());
        }

        boolean success = com.here.android.mpa.common.MapSettings.setIsolatedDiskCacheRootPath(diskCacheRoot,
                intentName);
        if (success) {
            Toast.makeText(m_activity, "if", Toast.LENGTH_SHORT).show();
            // Setting the isolated disk cache was not successful, please check if the path is valid and
            // ensure that it does not match the default location
            // (getExternalStorageDirectory()/.here-maps).
            // Also, ensure the provided intent name does not match the default intent name.
        } else {
            if (m_mapFragment != null) {
                /* Initialize the SupportMapFragment, results will be given via the called back. */
                m_mapFragment.init(new OnEngineInitListener() {
                    @Override
                    public void onEngineInitializationCompleted(OnEngineInitListener.Error error) {

                        if (error == OnEngineInitListener.Error.NONE) {

                            /*
                             * If no error returned from map fragment initialization, the map will be
                             * rendered on screen at this moment.Further actions on map can be provided
                             * by calling Map APIs.
                             */
                            m_map = m_mapFragment.getMap();
                            m_map.setTrafficInfoVisible(true);
                            /*
                             * Map center can be set to a desired location at this point.
                             * It also can be set to the current location ,which needs to be delivered by the PositioningManager.
                             * Please refer to the user guide for how to get the real-time location.
                             */

                            m_map.setLandmarksVisible(true);
                            m_map.setCenter(new GeoCoordinate(49.258576, -123.008268), Map.Animation.BOW);

                            final RoutePlan routePlan = new RoutePlan();

                            // these two waypoints cover suburban roads
                            routePlan.addWaypoint(new RouteWaypoint(new GeoCoordinate(48.98382, 2.50292)));
                            routePlan.addWaypoint(new RouteWaypoint(new GeoCoordinate(48.95602, 2.45939)));

                            try {
                                // calculate a route for navigation
                                CoreRouter coreRouter = new CoreRouter();
                                coreRouter.calculateRoute(routePlan, new CoreRouter.Listener() {
                                    @Override
                                    public void onProgress(int i) {

                                    }

                                    @Override
                                    public void onCalculateRouteFinished(List<RouteResult> list,
                                                                         RoutingError routingError) {
                                        if (routingError == RoutingError.NONE) {
                                            Route route = list.get(0).getRoute();

                                            // move the map to the first waypoint which is starting point of
                                            // the route
                                            m_map.setCenter(routePlan.getWaypoint(0).getNavigablePosition(),
                                                    Map.Animation.NONE);

                                            // setting MapUpdateMode to RoadView will enable automatic map
                                            // movements and zoom level adjustments
                                            NavigationManager.getInstance().setMapUpdateMode
                                                    (NavigationManager.MapUpdateMode.ROADVIEW);

                                            // adjust tilt to show 3D view
                                            m_map.setTilt(80);

                                            // adjust transform center for navigation experience in portrait
                                            // view
                                            m_mapTransformCenter = new PointF(m_map.getTransformCenter().x, (m_map
                                                    .getTransformCenter().y * 85 / 50));
                                            m_map.setTransformCenter(m_mapTransformCenter);

                                            // create a map marker to show current position
                                            Image icon = new Image();
                                            m_positionIndicatorFixed = new MapMarker();
                                            try {
                                                icon.setImageResource(R.drawable.ic_launcher_background);
                                                m_positionIndicatorFixed.setIcon(icon);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }

                                            m_positionIndicatorFixed.setVisible(true);
                                            m_positionIndicatorFixed.setCoordinate(m_map.getCenter());
                                            m_map.addMapObject(m_positionIndicatorFixed);

                                            m_mapFragment.getPositionIndicator().setVisible(false);

                                            NavigationManager.getInstance().setMap(m_map);

                                            // listen to real position updates. This is used when RoadView is
                                            // not active.
                                            PositioningManager.getInstance().addListener(
                                                    new WeakReference<PositioningManager.OnPositionChangedListener>(
                                                            mapPositionHandler));

                                            // listen to updates from RoadView which tells you where the map
                                            // center should be situated. This is used when RoadView is active.
                                            NavigationManager.getInstance().getRoadView().addListener(new
                                                    WeakReference<NavigationManager.RoadView.Listener>(roadViewListener));

                                            // start navigation simulation travelling at 13 meters per second
                                            NavigationManager.getInstance().simulate(route, 200);

                                        } else {
                                            Toast.makeText(m_activity,
                                                    "Error:route calculation returned error code: " + routingError,
                                                    Toast.LENGTH_LONG).show();

                                        }
                                    }


                                });
                            } catch (Exception e) {
                            }
                        }

                    }
                });
            }
        }
    }
    private PositioningManager.OnPositionChangedListener mapPositionHandler = new PositioningManager.OnPositionChangedListener() {
        @Override
        public void onPositionUpdated(PositioningManager.LocationMethod method, GeoPosition position,
                                      boolean isMapMatched) {
            if (NavigationManager.getInstance().getMapUpdateMode().equals(NavigationManager
                    .MapUpdateMode.NONE) && !m_returningToRoadViewMode)
                // use this updated position when map is not updated by RoadView.
                m_positionIndicatorFixed.setCoordinate(position.getCoordinate());
        }

        @Override
        public void onPositionFixChanged(PositioningManager.LocationMethod method,
                                         PositioningManager.LocationStatus status) {

        }
    };

    final private NavigationManager.RoadView.Listener roadViewListener = new NavigationManager.RoadView.Listener() {
        @Override
        public void onPositionChanged(GeoCoordinate geoCoordinate) {
            // an active RoadView provides coordinates that is the map transform center of it's
            // movements.
            m_mapTransformCenter = m_map.projectToPixel
                    (geoCoordinate).getResult();
        }
    };
}

//import java.io.File;
//import java.lang.ref.WeakReference;
//import java.util.List;
//
//import com.here.android.mpa.common.GeoBoundingBox;
//import com.here.android.mpa.common.GeoCoordinate;
//import com.here.android.mpa.common.GeoPosition;
//import com.here.android.mpa.common.OnEngineInitListener;
//import com.here.android.mpa.guidance.NavigationManager;
//import com.here.android.mpa.mapping.Map;
//import com.here.android.mpa.mapping.SupportMapFragment;
//import com.here.android.mpa.mapping.MapRoute;
//import com.here.android.mpa.routing.CoreRouter;
//import com.here.android.mpa.routing.Route;
//import com.here.android.mpa.routing.RouteOptions;
//import com.here.android.mpa.routing.RoutePlan;
//import com.here.android.mpa.routing.RouteResult;
//import com.here.android.mpa.routing.RouteWaypoint;
//import com.here.android.mpa.routing.Router;
//import com.here.android.mpa.routing.RoutingError;
//
//import android.support.v7.app.AppCompatActivity;
//import android.app.AlertDialog;
//import android.content.DialogInterface;
//import android.content.Intent;
//import android.content.pm.ApplicationInfo;
//import android.content.pm.PackageManager;
//import android.os.Bundle;
//import android.os.Environment;
//import android.util.Log;
//import android.view.View;
//import android.widget.Button;
//import android.widget.Toast;
//
///**
// * This class encapsulates the properties and functionality of the Map view.It also triggers a
// * turn-by-turn navigation from HERE Burnaby office to Langley BC.There is a sample voice skin
// * bundled within the SDK package to be used out-of-box, please refer to the Developer's guide for
// * the usage.
// */
//public class MapFragmentView {
//    private SupportMapFragment m_mapFragment;
//    private AppCompatActivity m_activity;
//    private Button m_naviControlButton;
//    private Map m_map;
//    private NavigationManager m_navigationManager;
//    private GeoBoundingBox m_geoBoundingBox;
//    private Route m_route;
//    private boolean m_ForeGroundServiceStarted;
//
//    public MapFragmentView(AppCompatActivity activity) {
//        m_activity = activity;
//        initMapFragment();
//
//    }
//
//    private SupportMapFragment getMapFragment() {
//        return (SupportMapFragment) m_activity.getSupportFragmentManager().findFragmentById(R.id.mapfragment);
//    }
//
//    private void initMapFragment() {
//        /* Locate the mapFragment UI element */
//        m_mapFragment = getMapFragment();
//
//        // Set path of isolated disk cache
//        String diskCacheRoot = Environment.getExternalStorageDirectory().getPath()
//                + File.separator + ".isolated-here-maps";
//        // Retrieve intent name from manifest
//        String intentName = "";
//        try {
//            ApplicationInfo ai = m_activity.getPackageManager().getApplicationInfo(m_activity.getPackageName(), PackageManager.GET_META_DATA);
//            Bundle bundle = ai.metaData;
//            intentName = bundle.getString("INTENT_NAME");
//        } catch (PackageManager.NameNotFoundException e) {
//            Log.e(this.getClass().toString(), "Failed to find intent name, NameNotFound: " + e.getMessage());
//        }
//
//        boolean success = com.here.android.mpa.common.MapSettings.setIsolatedDiskCacheRootPath(diskCacheRoot, intentName);
//        if (!success) {
//            // Setting the isolated disk cache was not successful, please check if the path is valid and
//            // ensure that it does not match the default location
//            // (getExternalStorageDirectory()/.here-maps).
//            // Also, ensure the provided intent name does not match the default intent name.
//        } else {
//            if (m_mapFragment != null) {
//                /* Initialize the SupportMapFragment, results will be given via the called back. */
//                m_mapFragment.init(new OnEngineInitListener() {
//                    @Override
//                    public void onEngineInitializationCompleted(OnEngineInitListener.Error error) {
//
//                        if (error == Error.NONE) {
//                            m_map = m_mapFragment.getMap();
//                            m_map.setCenter(new GeoCoordinate(49.259149, -123.008555),
//                                    Map.Animation.NONE);
//                            //Put this call in Map.onTransformListener if the animation(Linear/Bow)
//                            //is used in setCenter()
//                            m_map.setZoomLevel(13.2);
//                            /*
//                             * Get the NavigationManager instance.It is responsible for providing voice
//                             * and visual instructions while driving and walking
//                             */
//                            m_navigationManager = NavigationManager.getInstance();
//                            initNaviControlButton();
//                        } else {
//                            Toast.makeText(m_activity,
//                                    "ERROR: Cannot initialize Map with error " + error,
//                                    Toast.LENGTH_LONG).show();
//                        }
//                    }
//                });
//            }
//        }
//    }
//
//    private void createRoute() {
//        /* Initialize a CoreRouter */
//        CoreRouter coreRouter = new CoreRouter();
//
//        /* Initialize a RoutePlan */
//        RoutePlan routePlan = new RoutePlan();
//
//        /*
//         * Initialize a RouteOption.HERE SDK allow users to define their own parameters for the
//         * route calculation,including transport modes,route types and route restrictions etc.Please
//         * refer to API doc for full list of APIs
//         */
//        RouteOptions routeOptions = new RouteOptions();
//        /* Other transport modes are also available e.g Pedestrian */
//        routeOptions.setTransportMode(RouteOptions.TransportMode.CAR);
//        /* Disable highway in this route. */
//        routeOptions.setHighwaysAllowed(false);
//        /* Calculate the shortest route available. */
//        routeOptions.setRouteType(RouteOptions.Type.SHORTEST);
//        /* Calculate 1 route. */
//        routeOptions.setRouteCount(1);
//        /* Finally set the route option */
//        routePlan.setRouteOptions(routeOptions);
//
//        /* Define waypoints for the route */
//        /* START: 4350 Still Creek Dr */
//        RouteWaypoint startPoint = new RouteWaypoint(new GeoCoordinate(49.259149, -123.008555));
//        /* END: Langley BC */
//        RouteWaypoint destination = new RouteWaypoint(new GeoCoordinate(49.073640, -122.559549));
//
//        /* Add both waypoints to the route plan */
//        routePlan.addWaypoint(startPoint);
//        routePlan.addWaypoint(destination);
//
//        /* Trigger the route calculation,results will be called back via the listener */
//        coreRouter.calculateRoute(routePlan,
//                new Router.Listener<List<RouteResult>, RoutingError>() {
//
//                    @Override
//                    public void onProgress(int i) {
//                        /* The calculation progress can be retrieved in this callback. */
//                    }
//
//                    @Override
//                    public void onCalculateRouteFinished(List<RouteResult> routeResults,
//                                                         RoutingError routingError) {
//                        /* Calculation is done.Let's handle the result */
//                        if (routingError == RoutingError.NONE) {
//                            if (routeResults.get(0).getRoute() != null) {
//
//                                m_route = routeResults.get(0).getRoute();
//                                /* Create a MapRoute so that it can be placed on the map */
//                                MapRoute mapRoute = new MapRoute(routeResults.get(0).getRoute());
//
//                                /* Show the maneuver number on top of the route */
//                                mapRoute.setManeuverNumberVisible(true);
//
//                                /* Add the MapRoute to the map */
//                                m_map.addMapObject(mapRoute);
//
//                                /*
//                                 * We may also want to make sure the map view is orientated properly
//                                 * so the entire route can be easily seen.
//                                 */
//                                m_geoBoundingBox = routeResults.get(0).getRoute().getBoundingBox();
//                                m_map.zoomTo(m_geoBoundingBox, Map.Animation.NONE,
//                                        Map.MOVE_PRESERVE_ORIENTATION);
//
//                                startNavigation();
//                            } else {
//                                Toast.makeText(m_activity,
//                                        "Error:route results returned is not valid",
//                                        Toast.LENGTH_LONG).show();
//                            }
//                        } else {
//                            Toast.makeText(m_activity,
//                                    "Error:route calculation returned error code: " + routingError,
//                                    Toast.LENGTH_LONG).show();
//
//                        }
//                    }
//                });
//    }
//
//    private void initNaviControlButton() {
//        m_naviControlButton = (Button) m_activity.findViewById(R.id.start_nav);
//        m_naviControlButton.setText("start");
//        m_naviControlButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//
//            public void onClick(View v) {
//                /*
//                 * To start a turn-by-turn navigation, a concrete route object is required.We use
//                 * the same steps from Routing sample app to create a route from 4350 Still Creek Dr
//                 * to Langley BC without going on HWY.
//                 *
//                 * The route calculation requires local map data.Unless there is pre-downloaded map
//                 * data on device by utilizing MapLoader APIs,it's not recommended to trigger the
//                 * route calculation immediately after the MapEngine is initialized.The
//                 * INSUFFICIENT_MAP_DATA error code may be returned by CoreRouter in this case.
//                 *
//                 */
//                if (m_route == null) {
//                    createRoute();
//                } else {
//                    m_navigationManager.stop();
//                    /*
//                     * Restore the map orientation to show entire route on screen
//                     */
//                    m_map.zoomTo(m_geoBoundingBox, Map.Animation.NONE, 0f);
//                    m_naviControlButton.setText("started");
//                    m_route = null;
//                }
//            }
//        });
//    }
//
//    /*
//     * Android 8.0 (API level 26) limits how frequently background apps can retrieve the user's
//     * current location. Apps can receive location updates only a few times each hour.
//     * See href="https://developer.android.com/about/versions/oreo/background-location-limits.html
//     * In order to retrieve location updates more frequently start a foreground service.
//     * See https://developer.android.com/guide/components/services.html#Foreground
//     */
//    private void startForeGroundService() {
//        if (!m_ForeGroundServiceStarted) {
//            m_ForeGroundServiceStarted = true;
//            Intent startIntent = new Intent(m_activity, ForeGroundService.class);
//            startIntent.setAction(ForeGroundService.START_ACTION);
//            m_activity.getApplicationContext().startService(startIntent);
//        }
//    }
//
//    private void stopForeGroundService() {
//        if (m_ForeGroundServiceStarted) {
//            m_ForeGroundServiceStarted = false;
//            Intent stopIntent = new Intent(m_activity, ForeGroundService.class);
//            stopIntent.setAction(ForeGroundService.STOP_ACTION);
//            m_activity.getApplicationContext().startService(stopIntent);
//        }
//    }
//
//    private void startNavigation() {
//        m_naviControlButton.setText("stop");
//        /* Configure Navigation manager to launch navigation on current map */
//        m_navigationManager.setMap(m_map);
//
//        /*
//         * Start the turn-by-turn navigation.Please note if the transport mode of the passed-in
//         * route is pedestrian, the NavigationManager automatically triggers the guidance which is
//         * suitable for walking. Simulation and tracking modes can also be launched at this moment
//         * by calling either simulate() or startTracking()
//         */
//
//        /* Choose navigation modes between real time navigation and simulation */
//        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(m_activity);
//        alertDialogBuilder.setTitle("Navigation");
//        alertDialogBuilder.setMessage("Choose Mode");
//        alertDialogBuilder.setNegativeButton("Navigation",new DialogInterface.OnClickListener() {
//            public void onClick(DialogInterface dialoginterface, int i) {
//                m_navigationManager.startNavigation(m_route);
//                m_map.setTilt(60);
//                startForeGroundService();
//            };
//        });
//        alertDialogBuilder.setPositiveButton("Simulation",new DialogInterface.OnClickListener() {
//            public void onClick(DialogInterface dialoginterface, int i) {
//                m_navigationManager.simulate(m_route,60);//Simualtion speed is set to 60 m/s
//                m_map.setTilt(60);
//                startForeGroundService();
//            };
//        });
//        AlertDialog alertDialog = alertDialogBuilder.create();
//        alertDialog.show();
//        /*
//         * Set the map update mode to ROADVIEW.This will enable the automatic map movement based on
//         * the current location.If user gestures are expected during the navigation, it's
//         * recommended to set the map update mode to NONE first. Other supported update mode can be
//         * found in HERE Android SDK API doc
//         */
//        m_navigationManager.setMapUpdateMode(NavigationManager.MapUpdateMode.ROADVIEW);
//
//        /*
//         * NavigationManager contains a number of listeners which we can use to monitor the
//         * navigation status and getting relevant instructions.In this example, we will add 2
//         * listeners for demo purpose,please refer to HERE Android SDK API documentation for details
//         */
//        addNavigationListeners();
//    }
//
//    private void addNavigationListeners() {
//
//        /*
//         * Register a NavigationManagerEventListener to monitor the status change on
//         * NavigationManager
//         */
//        m_navigationManager.addNavigationManagerEventListener(
//                new WeakReference<NavigationManager.NavigationManagerEventListener>(
//                        m_navigationManagerEventListener));
//
//        /* Register a PositionListener to monitor the position updates */
//        m_navigationManager.addPositionListener(
//                new WeakReference<NavigationManager.PositionListener>(m_positionListener));
//    }
//
//    private NavigationManager.PositionListener m_positionListener = new NavigationManager.PositionListener() {
//        @Override
//        public void onPositionUpdated(GeoPosition geoPosition) {
//            /* Current position information can be retrieved in this callback */
//        }
//    };
//
//    private NavigationManager.NavigationManagerEventListener m_navigationManagerEventListener = new NavigationManager.NavigationManagerEventListener() {
//        @Override
//        public void onRunningStateChanged() {
//            Toast.makeText(m_activity, "Running state changed", Toast.LENGTH_SHORT).show();
//        }
//
//        @Override
//        public void onNavigationModeChanged() {
//            Toast.makeText(m_activity, "Navigation mode changed", Toast.LENGTH_SHORT).show();
//        }
//
//        @Override
//        public void onEnded(NavigationManager.NavigationMode navigationMode) {
//            Toast.makeText(m_activity, navigationMode + " was ended", Toast.LENGTH_SHORT).show();
//            stopForeGroundService();
//        }
//
//        @Override
//        public void onMapUpdateModeChanged(NavigationManager.MapUpdateMode mapUpdateMode) {
//            Toast.makeText(m_activity, "Map update mode is changed to " + mapUpdateMode,
//                    Toast.LENGTH_SHORT).show();
//        }
//
//        @Override
//        public void onRouteUpdated(Route route) {
//            Toast.makeText(m_activity, "Route updated", Toast.LENGTH_SHORT).show();
//        }
//
//        @Override
//        public void onCountryInfo(String s, String s1) {
//            Toast.makeText(m_activity, "Country info updated from " + s + " to " + s1,
//                    Toast.LENGTH_SHORT).show();
//        }
//    };
//
//    public void onDestroy() {
//        /* Stop the navigation when app is destroyed */
//        if (m_navigationManager != null) {
//            stopForeGroundService();
//            m_navigationManager.stop();
//        }
//    }
//}