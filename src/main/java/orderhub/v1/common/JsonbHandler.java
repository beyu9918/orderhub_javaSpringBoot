package orderhub.v1.common;

import java.sql.*;
import java.util.Map;
import org.apache.ibatis.type.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

public class JsonbHandler extends BaseTypeHandler<Map<String,Object>> {
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    public static String encode(Object value) { return JSON.writeValueAsString(value); }
    public static Map<String,Object> object(Object value) {
        return JSON.convertValue(value, new TypeReference<Map<String,Object>>() {});
    }
    @Override public void setNonNullParameter(PreparedStatement ps,int i,Map<String,Object> value,JdbcType type) throws SQLException {
        ps.setObject(i, encode(value), Types.OTHER);
    }
    private Map<String,Object> read(String value) {
        return value == null ? null : JSON.readValue(value, new TypeReference<Map<String,Object>>() {});
    }
    @Override public Map<String,Object> getNullableResult(ResultSet rs,String column) throws SQLException { return read(rs.getString(column)); }
    @Override public Map<String,Object> getNullableResult(ResultSet rs,int column) throws SQLException { return read(rs.getString(column)); }
    @Override public Map<String,Object> getNullableResult(CallableStatement cs,int column) throws SQLException { return read(cs.getString(column)); }
}
