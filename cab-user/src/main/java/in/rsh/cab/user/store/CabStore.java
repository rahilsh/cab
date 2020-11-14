package in.rsh.cab.user.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.json.JSONArray;
import org.json.JSONObject;

/** @author rahil */
public class CabStore {

  public static JSONArray cabs(float lat, float lon, boolean pink, String number) {
    Connection con = null;
    PreparedStatement prepStmt = null;
    try {
      String sql;
      con = getConnection();

      if (lat == 0.0 && lon == 0.0) {
        sql = "select * from cabs where isAvailable=true";
        if (pink) {
          sql = sql + " and type='pink'";
        }
        prepStmt = con.prepareStatement(sql);
      } else {
        if (pink) {
          sql =
              "SELECT number, ( 3959 * acos( cos( radians( ? ) ) * cos( radians( lat ) ) "
                  + "* cos( radians( lng ) - radians(?) ) sin( radians(?) ) "
                  + "* sin( radians( lat ) ) ) ) AS distance,type,isAvailable "
                  + "FROM cabs HAVING distance < 10 and type='pink' "
                  + "and isAvailable=true ORDER BY distance LIMIT 0 , 1;";
          prepStmt = con.prepareStatement(sql);
          prepStmt.setFloat(1, lat);
          prepStmt.setFloat(2, lon);
          prepStmt.setFloat(3, lat);

        } else if (number != null) {
          sql =
              "SELECT  number,( 3959 * acos( cos( radians(?) ) * cos( radians( lat ) ) "
                  + "* cos( radians( lng ) - radians(?) ) + sin( radians(?) ) "
                  + "* sin( radians( lat ) ) ) ) AS distance and number= ? FROM cabs;";
          prepStmt = con.prepareStatement(sql);
          prepStmt.setFloat(1, lat);
          prepStmt.setFloat(2, lon);
          prepStmt.setFloat(3, lat);
          prepStmt.setString(4, number);
        } else {
          sql =
              "SELECT  number,( 3959 * acos( cos( radians(?) ) * cos( radians( lat ) ) "
                  + "* cos( radians( lng ) - radians(?) ) + sin( radians(?) ) "
                  + "* sin( radians( lat ) ) ) ) AS distance,type,isAvailable "
                  + "and isAvailable=true FROM cabs HAVING distance < 10  "
                  + "ORDER BY distance LIMIT 0 , 1;";
          prepStmt = con.prepareStatement(sql);
          prepStmt.setFloat(1, lat);
          prepStmt.setFloat(2, lon);
          prepStmt.setFloat(3, lat);
        }
      }
      ResultSet rs = prepStmt.executeQuery(sql);
      JSONArray array = new JSONArray();
      while (rs.next()) {
        JSONObject cabs = new JSONObject();
        cabs.put("number", rs.getString(1));
        if (lat == 0.0 && lon == 0.0) {
          cabs.put("owner", rs.getString(2));
          cabs.put("location", rs.getString(3));
          cabs.put("type", rs.getString(4));
          cabs.put("isAvailable", rs.getBoolean(5));
        }
        if (number != null) {
          cabs.put("distance", rs.getString(1));
        }
        array.put(cabs);
      }
      return array;
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      closeIfNotNull(con);
      closeIfNotNull(prepStmt);
    }
    return null;
  }

  private static void closeIfNotNull(AutoCloseable autoCloseable) {
    if (autoCloseable != null) {
      try {
        autoCloseable.close();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static Connection getConnection() throws SQLException, ClassNotFoundException {
    Class.forName("com.mysql.jdbc.Driver");
    return DriverManager.getConnection("jdbc:mysql://localhost:3306/test", "root", "");
  }

  public static boolean book(String number, JSONObject user) {
    Connection con = null;
    PreparedStatement insertQuery = null;
    PreparedStatement updateTotal = null;
    try {
      con = getConnection();
      String insertString = "insert into customer (number,name,cabnumber,source) values(?,?,?,?)";
      String updateStatement = "update cabs set isAvailable = false where number = ?";

      con.setAutoCommit(false);
      insertQuery = con.prepareStatement(insertString);
      updateTotal = con.prepareStatement(updateStatement);

      insertQuery.setString(1, user.get("number").toString());
      insertQuery.setString(2, user.get("name").toString());
      insertQuery.setString(3, number);
      insertQuery.setString(4, user.get("lat").toString() + "," + user.get("lon").toString());
      insertQuery.execute();

      updateTotal.setString(1, number);
      updateTotal.executeUpdate();
      con.commit();
      return true;
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      closeIfNotNull(insertQuery);
      closeIfNotNull(con);
      closeIfNotNull(updateTotal);
    }
    return false;
  }

  public static void main(String[] args) {
    try {
      System.out.println(
          CabStore.book(
              "1234",
              new JSONObject(
                  "{\"number\":\"9999\",\"name\":\"test\",\"cabnumber\":\"1234\",\"source\":\"00.00\"}")));
      //System.out.println(CabStore.cabs());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void update(String lat, String lon, double fare) {
    // TODO Auto-generated method stub
  }
}
