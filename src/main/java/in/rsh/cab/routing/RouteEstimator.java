package in.rsh.cab.routing;

import in.rsh.cab.geography.GeoPoint;

public interface RouteEstimator {

  RouteEstimate estimate(GeoPoint origin, GeoPoint destination);
}
