package com.capstone.etp.executor.config;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UUIDTypeHandlerTest {

    private final UUIDTypeHandler handler = new UUIDTypeHandler();

    @Test
    void setNonNullParameter_usesPreparedStatementSetObject() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        UUID uuid = UUID.randomUUID();

        handler.setNonNullParameter(ps, 1, uuid, JdbcType.OTHER);

        verify(ps).setObject(1, uuid);
    }

    @Test
    void getNullableResult_byColumnName_handlesNullAndValue() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        UUID uuid = UUID.randomUUID();

        when(rs.getObject("id")).thenReturn(null, uuid.toString());

        assertNull(handler.getNullableResult(rs, "id"));
        assertEquals(uuid, handler.getNullableResult(rs, "id"));
    }

    @Test
    void getNullableResult_byColumnIndex_handlesNullAndValue() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        UUID uuid = UUID.randomUUID();

        when(rs.getObject(2)).thenReturn(null, uuid.toString());

        assertNull(handler.getNullableResult(rs, 2));
        assertEquals(uuid, handler.getNullableResult(rs, 2));
    }

    @Test
    void getNullableResult_callableStatement_handlesNullAndValue() throws Exception {
        CallableStatement cs = mock(CallableStatement.class);
        UUID uuid = UUID.randomUUID();

        when(cs.getObject(3)).thenReturn(null, uuid.toString());

        assertNull(handler.getNullableResult(cs, 3));
        assertEquals(uuid, handler.getNullableResult(cs, 3));
    }
}
