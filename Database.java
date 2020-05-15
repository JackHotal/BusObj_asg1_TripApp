package dblib;

import javafx.util.Pair;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;
//import com.microsoft.sqlserver.jdbc.SQLServerDriver;
import java.sql.DriverManager;
import java.util.GregorianCalendar;

public class Database {

    static private String mservername;
    static private String mdbname;
    static private String url;
    static private Connection mcn;

    static {
        mservername = "MSSQLSERVER";
        mdbname = "trip";
    }

    public Database(String uid, String pass) {

        setConnection(uid, pass);
    }

    public boolean IsConnected()    {
        return (mcn != null ? true : false);
    }

    public void setConnection(String uid, String pass) {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            String connectionUrl = "jdbc:sqlserver://localhost\\" + mservername
                    + ";databaseName=" + mdbname + ";user=" + uid + ";password=" + pass + ";";
            mcn = DriverManager.getConnection(connectionUrl);
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
            System.out.println("Error code:" + ex.getErrorCode());//ex.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    //methods

    public List<String> getCustomerTrips(String cid) {

        ArrayList<String> rval = new ArrayList<String>();
        try {

            Statement s = mcn.createStatement();
            String sql = String.format("Select DISTINCT t.tid from [Trip] t inner join Segment s ON t.Tid = s.Tid WHERE t.Cid = %s",cid);
            ResultSet rs = s.executeQuery(sql);

            while (rs.next()) {
                int tid = rs.getInt(1);

                rval.add(String.format("%s", tid));
            }

            s.close();
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

        }
        return rval;
    }

    public List<String> List(String tid) {
        ArrayList<String> rval = new ArrayList<String>();
        try {

            Statement s = mcn.createStatement();
            String sql = String.format("SELECT DISTINCT Trip.Tid, Segment.SDate,(SELECT Location.LName " +
                    "FROM Location WHERE Location.Lid = Segment.Origin) AS Orig,(SELECT Location.LName FROM " +
                    "Location WHERE Location.Lid=Segment.Destination) AS Dest, Segment.Price, Segment.TMode FROM Segment, " +
                    "Trip, Location WHERE Trip.Tid=Segment.Tid AND Trip.Tid=%s", tid);
            //SELECT DISTINCT Trip.Tid, Segment.SDate,(SELECT Location.LName FROM Location WHERE Location.Lid = Segment.Origin) AS Orig,(SELECT Location.LName FROM Location WHERE Location.Lid=Segment.Destination) AS Dest, Segment.Price, Segment.TMode FROM Segment, Trip, Location WHERE Trip.Tid=Segment.Tid AND Trip.Tid=%s
            //String sql = String.format("Select c.pid,c.quantity,c.price from [Orders] b INNER JOIN OrderDetails c ON b.oid=c.oid where b.tid =%s" , tid);
            ResultSet rs = s.executeQuery(sql);
            String buf ="";
            while (rs.next()) {

                String tripId = rs.getString(1);
                java.sql.Date date = rs.getDate(2);
                String orig = rs.getString(3);
                String dest = rs.getString(4);
                float price = rs.getFloat(5);

                buf = String.format("%s,%s,%s,%s,%.2f",tripId, date, orig, dest, price);
                rval.add(buf);
            }

            s.close();
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

        }
        return rval;
    }


    //list#2
    public String List(String tidE, String oidE) {
        String buf = "0";
        try {

            Statement s = mcn.createStatement();
            String sql = String.format("SELECT * " +
                    "FROM Segment " +
                    "WHERE Segment.Tid=%s AND Segment.Origin=%s", tidE, oidE);
            ResultSet rs = s.executeQuery(sql);
            while (rs.next()) {

                String mode = rs.getString(6);
                java.sql.Date date = rs.getDate(4);
                String orig = rs.getString(2);
                String dest = rs.getString(3);
                float price = rs.getFloat(5);

                buf = String.format("%s,%s,%s,%.2f,%s", orig, dest, date, price, mode);
            }

            s.close();
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

        }
        return buf;
    }


    public int edit(String tidE, String oidE, String priceE){
//        If the price is 0 it deletes the segment otherwise it updates the segment with the new price.
//        Either way the trip commission has to be updated. It returns the new price including commission
        int r = 0;
        String comRate = "0.1";
        try {
            if (priceE.equals("0")) {
                Statement s = mcn.createStatement();
                String sql = String.format("DELETE " +
                        "FROM Segment " +
                        "WHERE Segment.Tid=%s AND Segment.Origin=%s", tidE, oidE);
                s.executeUpdate(sql);
                s.close();
            } else {
                Statement s = mcn.createStatement();
                String sql = String.format("UPDATE Segment SET Price = %s " +
                        "WHERE Segment.Tid=%s AND Segment.Origin=%s", priceE, tidE, oidE);
                s.executeUpdate(sql);

                Statement s1 = mcn.createStatement();
                String sql1 = String.format("UPDATE Trip SET Commission = " +
                        "%s*(Select SUM(Segment.Price) FROM Segment WHERE Segment.Tid=%s) " +
                        "WHERE Tid = '%s';", comRate, tidE, tidE);
                s1.executeUpdate(sql1);

                s.close();
                s1.close();
            }
        }
        catch(SQLException sqle){ sqle.printStackTrace(); }
            finally{
                try {
                    Statement s1 = mcn.createStatement();
                    String sql1 = String.format("SELECT SUM(Segment.Price) FROM Segment " +
                            "WHERE Segment.Tid=" + tidE);
                    ResultSet rs = s1.executeQuery(sql1);
                    rs.next();
                    r = rs.getInt(1);
                    r *= 1.1;
                    s1.close();

                    Statement s2 = mcn.createStatement();
                    String sql2 = String.format("UPDATE Trip SET Commission = %s*(Select SUM(Segment.Price) " +
                            "FROM Segment WHERE Segment.Tid=%s) WHERE Tid = '%s';",comRate,tidE,tidE);
                    s2.executeUpdate(sql2);

                    } catch (SQLException sqle) { sqle.printStackTrace(); }
                    finally { return r; }
            }
    }



    //book

    public String[] book(String CustomerID, float commission, List<String> segmentList){

        String comRate = "0.1";
        Double totalPrice = 0.0;
        int tid = 0;

        PreparedStatement pst = null;
        try{
            //get new Tid
            pst = mcn.prepareStatement(
                    "SELECT Max(Tid) FROM Trip");
            ResultSet rs = pst.executeQuery();

            //Make a new tid
            while(rs.next())
                tid = rs.getInt(1) + 1;
            pst.close();
            //System.out.println("db trip id:" +tid);



            //create sql transactions to pass to transact
            int nt = 2*segmentList.size() +1;
            String [] sql = new String[nt];
            sql[0] = String.format("Insert Into Trip(Tid,Cid,Commission) Values (%s, %s, %f);",tid,CustomerID,commission);

            for (int i=0; i < nt-1; i+=2 ) {
                String [] vals = segmentList.get(i/2).split(",");

                sql[i+1] = String.format("INSERT INTO Segment (Tid, Origin, Destination, SDate, Price, TMode) VALUES (%s, %s, %s, '%s', %s, '%s');",tid ,vals[0], vals[1], vals[2], vals[3], vals[4]);
                sql[i+2] = String.format("UPDATE Trip SET Commission = %s*(Select SUM(Segment.Price) FROM Segment WHERE Segment.Tid=%s) WHERE Tid = '%s';",comRate,tid,tid);
            }

            try{
                TransactSQL(sql);
            } catch (Exception e) {
                tid = 0;
            }
            //if exception, report back

            //query total price
            Statement s = mcn.createStatement();
            String sql2;
            sql2 = "SELECT SUM(Segment.Price) FROM Segment " +
                    "WHERE Segment.Tid=" + tid;
            rs = s.executeQuery(sql2);
            rs.next();
            String commissionTemp = rs.getString(1);
            commission = Float.parseFloat(commissionTemp);
            totalPrice = commission * 1.1;
            commission = commission * .1f;

        } catch (SQLException sqle) {
            sqle.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

        }

        String [] ret = new String [2];
        String formattedPrice = String.format("%.2f", totalPrice);
        ret[0] = formattedPrice;
        ret[1] = Integer.toString(tid);


        return ret;
    }


    private void TransactSQL(String[] sql) {
        Statement st = null;
        //Double n = 0.0;
        try {
            st = mcn.createStatement();
            //System.out.println(mcn.getAutoCommit());
            mcn.setAutoCommit(false);

            for (int i = 0; i < sql.length; i++) {
                st.executeUpdate(sql[i]);
            }
            //mcn.rollback();
            mcn.commit();

        } catch (SQLException ex) {
            try {
                //rollback if there is an error
                mcn.rollback();
                ex.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (st != null) {
                    st.close();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }



}
