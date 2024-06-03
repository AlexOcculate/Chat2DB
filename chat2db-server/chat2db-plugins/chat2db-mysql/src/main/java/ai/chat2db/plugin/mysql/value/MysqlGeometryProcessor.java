package ai.chat2db.plugin.mysql.value;

import ai.chat2db.plugin.mysql.value.template.MysqlDmlValueTemplate;
import ai.chat2db.spi.jdbc.DefaultValueProcessor;
import ai.chat2db.spi.model.JDBCDataValue;
import ai.chat2db.spi.model.SQLDataValue;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * @author: zgq
 * @date: 2024年06月01日 12:42
 */
public class MysqlGeometryProcessor extends DefaultValueProcessor {


    @Override
    public String convertSQLValueByType(SQLDataValue dataValue) {
        return wrap(dataValue.getValue());
    }

    @Override
    public String convertJDBCValueByType(JDBCDataValue dataValue) {
        try {
            InputStream inputStream = dataValue.getBinaryStream();
            Geometry dbGeometry = null;
            if (inputStream != null) {

                //convert the stream to a byte[] array
                //so it can be passed to the WKBReader
                byte[] buffer = new byte[255];

                int bytesRead = 0;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }

                byte[] geometryAsBytes = baos.toByteArray();

                if (geometryAsBytes.length < 5) {
                    throw new Exception("Invalid geometry inputStream - less than five bytes");
                }

                //first four bytes of the geometry are the SRID,
                //followed by the actual WKB.  Determine the SRID
                //here
                byte[] sridBytes = new byte[4];
                System.arraycopy(geometryAsBytes, 0, sridBytes, 0, 4);
                boolean bigEndian = (geometryAsBytes[4] == 0x00);

                int srid = 0;
                if (bigEndian) {
                    for (int i = 0; i < sridBytes.length; i++) {
                        srid = (srid << 8) + (sridBytes[i] & 0xff);
                    }
                } else {
                    for (int i = 0; i < sridBytes.length; i++) {
                        srid += (sridBytes[i] & 0xff) << (8 * i);
                    }
                }

                //use the JTS WKBReader for WKB parsing
                WKBReader wkbReader = new WKBReader();

                //copy the byte array, removing the first four
                //SRID bytes
                byte[] wkb = new byte[geometryAsBytes.length - 4];
                System.arraycopy(geometryAsBytes, 4, wkb, 0, wkb.length);
                dbGeometry = wkbReader.read(wkb);
                dbGeometry.setSRID(srid);
            }
            return dbGeometry != null ? dbGeometry.toString() : null;
        } catch (Exception e) {
            return super.getJdbcValue(dataValue);
        }
    }


    @Override
    public String convertJDBCValueStrByType(JDBCDataValue dataValue) {
        return wrap(convertJDBCValueByType(dataValue));
    }

    private String wrap(String value) {
        return String.format(MysqlDmlValueTemplate.GEOMETRY_TEMPLATE, value);
    }

}
