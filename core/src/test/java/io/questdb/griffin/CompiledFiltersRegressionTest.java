/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2022 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.griffin;

import io.questdb.Metrics;
import io.questdb.cairo.AbstractCairoTest;
import io.questdb.cairo.security.AllowAllCairoSecurityContext;
import io.questdb.cairo.sql.*;
import io.questdb.griffin.engine.functions.bind.BindVariableServiceImpl;
import io.questdb.griffin.engine.table.CompiledFilterRecordCursorFactory;
import io.questdb.log.Log;
import io.questdb.log.LogFactory;
import io.questdb.std.Misc;
import io.questdb.std.str.StringSink;
import io.questdb.test.tools.TestUtils;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(Parameterized.class)
public class CompiledFiltersRegressionTest extends AbstractCairoTest {

    private static final Log LOG = LogFactory.getLog(CompiledFiltersRegressionTest.class);
    private static final int N_SIMD_NO_TAIL = 128;
    private static final int N_SIMD_WITH_TAIL = N_SIMD_NO_TAIL + 3;

    @Parameterized.Parameters(name = "{0}")
    public static Object[] data() {
        return new Object[] { JitMode.SCALAR, JitMode.VECTORIZED };
    }

    @Parameterized.Parameter
    public JitMode jitMode;

    private static final StringSink jitSink = new StringSink();
    private static BindVariableService bindVariableService;
    private static SqlExecutionContext sqlExecutionContext;
    private static SqlCompiler compiler;
    private static Metrics metrics = Metrics.enabled();

    @BeforeClass
    public static void setUpStatic() {
        AbstractCairoTest.setUpStatic();
        compiler = new SqlCompiler(engine);
        bindVariableService = new BindVariableServiceImpl(configuration);
        sqlExecutionContext = new SqlExecutionContextImpl(engine, 1)
                .with(
                        AllowAllCairoSecurityContext.INSTANCE,
                        bindVariableService,
                        null,
                        -1,
                        null);
        bindVariableService.clear();
    }

    @AfterClass
    public static void tearDownStatic() {
        AbstractCairoTest.tearDownStatic();
        compiler.close();
    }

    @Override
    @Before
    public void setUp() {
        super.setUp();
        bindVariableService.clear();
    }

    @Test
    public void testIntFloatMixed() throws Exception {
        final String query = "select * from x where -2*f32 + 2.0*i32 = 0";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " cast(x as byte) i8," +
                " cast(x as short) i16," +
                " cast(x as int) i32," +
                " cast(x as long) i64," +
                " cast(x as float) f32," +
                " cast(x as double) f64" +
                " from long_sequence(10)) timestamp(k) partition by DAY";
        assertQuery(query, ddl);
    }

    @Test
    public void testEqConst() throws Exception {
        final String query = "select * from x where i8 = 1 and i16 = 1 and i32 = 1 and i64 = 1 and f32 = 1.0 and f64 = 1.0";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " cast(x as byte) i8," +
                " cast(x as short) i16," +
                " cast(x as int) i32," +
                " cast(x as long) i64," +
                " cast(x as float) f32," +
                " cast(x as double) f64" +
                " from long_sequence(10)) timestamp(k) partition by DAY";
        assertQuery(query, ddl);
    }

    @Test
    public void testNotEqConst() throws Exception {
        final String query = "select * from x where i8 <> 1 and i16 <> 1 and i32 <> 1 and i64 <> 1 and f32 <> 1.0000 and f64 <> 1.0";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " cast(x as byte) i8," +
                " cast(x as short) i16," +
                " cast(x as int) i32," +
                " cast(x as long) i64," +
                " cast(x as float) f32," +
                " cast(x as double) f64" +
                " from long_sequence(10)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testLtConst() throws Exception {
        final String query = "select * from x where i8 < 2 and i16 < 2 and i32 < 2 and i64 < 2 and f32 < 2.0000 and f64 < 2.0";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " cast(x as byte) i8," +
                " cast(x as short) i16," +
                " cast(x as int) i32," +
                " cast(x as long) i64," +
                " cast(x as float) f32," +
                " cast(x as double) f64" +
                " from long_sequence(10)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testLeConst() throws Exception {
        final String query = "select * from x where i8 <= 2 and i16 <= 2 and i32 <= 2 and i64 <= 2 and f32 <= 2.0000 and f64 <= 2.0";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " cast(x as byte) i8," +
                " cast(x as short) i16," +
                " cast(x as int) i32," +
                " cast(x as long) i64," +
                " cast(x as float) f32," +
                " cast(x as double) f64" +
                " from long_sequence(10)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testGtConst() throws Exception {
        final String query = "select * from x where i8 > 9 and i16 > 9 and i32 > 9 and i64 > 9 and f32 > 9.0000 and f64 > 9.0";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " cast(x as byte) i8," +
                " cast(x as short) i16," +
                " cast(x as int) i32," +
                " cast(x as long) i64," +
                " cast(x as float) f32," +
                " cast(x as double) f64" +
                " from long_sequence(10)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testGeConst() throws Exception {
        final String query = "select * from x where i8 >= 9 and i16 >= 9 and i32 >= 9 and i64 >= 9 and f32 >= 9.0000 and f64 >= 9.0";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " cast(x as byte) i8," +
                " cast(x as short) i16," +
                " cast(x as int) i32," +
                " cast(x as long) i64," +
                " cast(x as float) f32," +
                " cast(x as double) f64" +
                " from long_sequence(10)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testAddConst() throws Exception {
        final String query = "select * from x where i8 + 1 = 10 and i16 + 1 = 10 and i32 + 1 = 10 and i64 + 1 = 10 " +
                "and f32 + 1.0 = 10.0 and f64 + 1.0 = 10.0";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " cast(x as byte) i8," +
                " cast(x as short) i16," +
                " cast(x as int) i32," +
                " cast(x as long) i64," +
                " cast(x as float) f32," +
                " cast(x as double) f64" +
                " from long_sequence(10)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testSubConst() throws Exception {
        final String query = "select * from x where i8 - 1 = 2 and i16 - 1 = 2 and i32 - 1 = 2 and i64 - 1 = 2 " +
                "and f32 - 1.0 = 2.0 and f64 - 1.0 = 2.0";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " cast(x as byte) i8," +
                " cast(x as short) i16," +
                " cast(x as int) i32," +
                " cast(x as long) i64," +
                " cast(x as float) f32," +
                " cast(x as double) f64" +
                " from long_sequence(10)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testMulConst() throws Exception {
        final String query = "select * from x where i8 * 2 = 4 and i16 * 2 = 4 and i32 * 2 = 4 and i64 * 2 = 4" +
                " and f32 * 2.0 = 4.0 and f64 * 2.0 = 4.0";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " cast(x as byte) i8," +
                " cast(x as short) i16," +
                " cast(x as int) i32," +
                " cast(x as long) i64," +
                " cast(x as float) f32," +
                " cast(x as double) f64" +
                " from long_sequence(10)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testDivConst() throws Exception {
        final String query = "select * from x where i8 / 2 = 4 and i16 / 2 = 4 and i32 / 2 = 4 and i64 / 2 = 4" +
                " and f32 / 2.0 = 4.0 and f64 / 2.0 = 4.0";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " cast(x as byte) i8," +
                " cast(x as short) i16," +
                " cast(x as int) i32," +
                " cast(x as long) i64," +
                " cast(x as float) f32," +
                " cast(x as double) f64" +
                " from long_sequence(10)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testIntFloatCast() throws Exception {
        final String query = "select * from x where " +
                "i8 = f32 and " +
                "i8 = f64 and " +
                "i16 = f32 and " +
                "i16 = f64 and " +
                "i32 = f32 and " +
                "i32 = f64 and " +
                "i64 = f32 and " +
                "i64 = f64";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " cast(x as byte) i8," +
                " cast(x as short) i16," +
                " cast(x as int) i32," +
                " cast(x as long) i64," +
                " cast(x as float) f32," +
                " cast(x as double) f64" +
                " from long_sequence(10)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testNegConst() throws Exception {
        final String query = "select * from x where 20 + -i8 = 10 and -f32 * 2 = -20.0";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " cast(x as byte) i8," +
                " cast(x as short) i16," +
                " cast(x as int) i32," +
                " cast(x as long) i64," +
                " cast(x as float) f32," +
                " cast(x as double) f64" +
                " from long_sequence(10)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testOrNot() throws Exception {
        final String query = "select * from x where i8 / 2 = 4 or i8 > 6 and not f64 = 10 and not i64 = 7";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " cast(x as byte) i8," +
                " cast(x as short) i16," +
                " cast(x as int) i32," +
                " cast(x as long) i64," +
                " cast(x as float) f32," +
                " cast(x as double) f64" +
                " from long_sequence(10)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testNull() throws Exception {
        final String query = "select * from x where i8 <> null and i16 <> null and i32 <> null and i64 <> null and f32 <> null and f64 <> null";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " cast(x as byte) i8," +
                " cast(x as short) i16," +
                " cast(x as int) i32," +
                " cast(x as long) i64," +
                " cast(x as float) f32," +
                " cast(x as double) f64" +
                " from long_sequence(5)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testSelectAllTypesFromRecord() throws Exception {
        final String query = "select * from x where b = true and kk < 10";
        final String ddl = "create table x as (select" +
                " cast(x as int) kk," +
                " rnd_int() a," +
                " rnd_boolean() b," +
                " rnd_str(1,1,2) c," +
                " rnd_double(2) d," +
                " rnd_float(2) e," +
                " rnd_short(10,1024) f," +
                " rnd_date(to_date('2015', 'yyyy'), to_date('2016', 'yyyy'), 2) g," +
                " rnd_symbol(4,4,4,2) i," +
                " rnd_long() j," +
                " timestamp_sequence(400000000000, 500000000) k," +
                " rnd_byte(2,50) l," +
                " rnd_bin(10, 20, 2) m," +
                " rnd_str(5,16,2) n," +
                " rnd_char() cc," +
                " rnd_long256() l2," +
                " rnd_geohash(1) hash1b," +
                " rnd_geohash(2) hash2b," +
                " rnd_geohash(3) hash3b," +
                " rnd_geohash(5) hash1c," +
                " rnd_geohash(10) hash2c," +
                " rnd_geohash(20) hash4c," +
                " rnd_geohash(40) hash8c" +
                " from long_sequence(100)) timestamp(k) partition by DAY";

        assertQuery(query, ddl);
    }

    @Test
    public void testI32Nulls() throws Exception {
        final String query = "select * from x where i32a = null or i32b = null";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_int(-10, 10, 1) i32a," +
                " rnd_int(-10, 10, 1) i32b" +
                " from long_sequence(11)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testI32NotNulls() throws Exception {
        final String query = "select * from x where i32a <> null and i32b <> null";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_int(-10, 10, 1) i32a," +
                " rnd_int(-10, 10, 1) i32b" +
                " from long_sequence(11)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testI32NegNulls() throws Exception {
        final String query = "select * from x where -i32a = null or -i32b = null";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_int(-10, 10, 1) i32a," +
                " rnd_int(-10, 10, 1) i32b" +
                " from long_sequence(11)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testI32AddNulls() throws Exception {
        final String query = "select * from x where i32a + i32b = null";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_int(-10, 10, 1) i32a," +
                " rnd_int(-10, 10, 1) i32b" +
                " from long_sequence(11)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testI32SubNulls() throws Exception {
        final String query = "select * from x where i32a - i32b = null";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_int(-10, 10, 1) i32a," +
                " rnd_int(-10, 10, 1) i32b" +
                " from long_sequence(11)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testI32MulNulls() throws Exception {
        final String query = "select * from x where i32a * i32b = null";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_int(-10, 10, 1) i32a," +
                " rnd_int(-10, 10, 1) i32b" +
                " from long_sequence(11)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testI32DivNulls() throws Exception {
        final String query = "select * from x where i32a / i32b = null";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_int(-10, 10, 1) i32a," +
                " rnd_int(-10, 10, 1) i32b" +
                " from long_sequence(11)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testI32CmpIgnoreNulls() throws Exception {
        final String query = "select * from x where i32a > i32b or i32a >= i32b or i32a < i32b or i32a <= i32b";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_int(-10, 10, 1) i32a," +
                " rnd_int(-10, 10, 1) i32b" +
                " from long_sequence(11)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testI64Nulls() throws Exception {
        final String query = "select * from x where i64a = null or i64b = null";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_long(-10, 10, 1) i64a," +
                " rnd_long(-10, 10, 1) i64b" +
                " from long_sequence(11)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testI64NotNulls() throws Exception {
        final String query = "select * from x where i64a <> null and i64b <> null";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_long(-10, 10, 1) i64a," +
                " rnd_long(-10, 10, 1) i64b" +
                " from long_sequence(11)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testI64Neg() throws Exception {
        final String query = "select * from x where -a = -4";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " cast(x as long) a, " +
                " cast(x as short) b" +
                " from long_sequence(100)) timestamp(k)";

        assertQuery(query, ddl);
    }

    @Test
    public void testI64NegNulls() throws Exception {
        final String query = "select * from x where -i64a <> null and -i64b = null";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_long(-10, 10, 1) i64a," +
                " rnd_long(-10, 10, 1) i64b" +
                " from long_sequence(11)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testI64AddNulls() throws Exception {
        final String query = "select * from x where i64a + i64b = null";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_long(-10, 10, 1) i64a," +
                " rnd_long(-10, 10, 1) i64b" +
                " from long_sequence(11)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testI64SubNulls() throws Exception {
        final String query = "select * from x where i64a - i64b = null";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_long(-10, 10, 1) i64a," +
                " rnd_long(-10, 10, 1) i64b" +
                " from long_sequence(11)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testI64MulNulls() throws Exception {
        final String query = "select * from x where i64a * i64b = null";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_long(-10, 10, 1) i64a," +
                " rnd_long(-10, 10, 1) i64b" +
                " from long_sequence(11)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testI64DivNulls() throws Exception {
        final String query = "select * from x where i64a / i64b = null";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_long(-10, 10, 1) i64a," +
                " rnd_long(-10, 10, 1) i64b" +
                " from long_sequence(11)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testI64CmpIgnoreNulls() throws Exception {
        final String query = "select * from x where i64a > i64b or i64a >= i64b or i64a < i64b or i64a <= i64b";
        // final String query = "select * from x where i64a <> null and i64b <> null";
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_long(-10, 10, 1) i64a," +
                " rnd_long(-10, 10, 1) i64b" +
                " from long_sequence(11)) timestamp(k)";
        assertQuery(query, ddl);
    }

    @Test
    public void testIntegerColumnToConstComparisons() throws Exception {
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_byte() i8," +
                " rnd_short() i16," +
                " rnd_int() i32," +
                " rnd_long() i64 " +
                " from long_sequence(" + N_SIMD_WITH_TAIL + ")) timestamp(k) partition by DAY";
        FilterGenerator gen = new FilterGenerator()
                .withOptionalNegation().withAnyOf("i8", "i16", "i32", "i64")
                .withComparisonOperator()
                .withAnyOf("-50", "0", "50");
        assertGeneratedQuery("select * from x", ddl, gen);
    }

    @Test
    public void testFloatColumnToConstComparisons() throws Exception {
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_float() f32," +
                " rnd_double() f64 " +
                " from long_sequence(" + N_SIMD_WITH_TAIL + ")) timestamp(k) partition by DAY";
        FilterGenerator gen = new FilterGenerator()
                .withOptionalNegation().withAnyOf("f32", "f64")
                .withComparisonOperator()
                .withAnyOf("-50", "-25.5", "0", "0.0", "0.000", "25.5", "50");
        assertGeneratedQuery("select * from x", ddl, gen);
    }

    @Test
    public void testColumnArithmetics() throws Exception {
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_byte() i8," +
                " rnd_short() i16," +
                " rnd_int() i32," +
                " rnd_long() i64," +
                " rnd_float() f32," +
                " rnd_double() f64 " +
                " from long_sequence(" + N_SIMD_WITH_TAIL + ")) timestamp(k) partition by DAY";
        FilterGenerator gen = new FilterGenerator()
                .withOptionalNegation().withAnyOf("i8", "i16", "i32", "i64", "f32", "f64")
                .withArithmeticOperator()
                .withOptionalNegation().withAnyOf("i8", "i16", "i32", "i64", "f32", "f64")
                .withAnyOf(" = 0");
        assertGeneratedQuery("select * from x", ddl, gen);
    }

    @Test
    public void testConstantColumnArithmetics() throws Exception {
        final String ddl = "create table x as " +
                "(select timestamp_sequence(400000000000, 500000000) as k," +
                " rnd_byte() i8," +
                " rnd_short() i16," +
                " rnd_int() i32," +
                " rnd_long() i64," +
                " rnd_float() f32," +
                " rnd_double() f64 " +
                " from long_sequence(" + N_SIMD_WITH_TAIL + ")) timestamp(k) partition by DAY";
        FilterGenerator gen = new FilterGenerator()
                .withAnyOf("3", "-3.5")
                .withArithmeticOperator()
                .withOptionalNegation().withAnyOf("i8", "i16", "i32", "i64")
                .withAnyOf(" + ")
                .withAnyOf("42.5", "-42")
                .withArithmeticOperator()
                .withOptionalNegation().withAnyOf("f32", "f64")
                .withAnyOf(" > 0");
        assertGeneratedQuery("select * from x", ddl, gen);
    }

    private void assertGeneratedQuery(CharSequence baseQuery, CharSequence ddl, FilterGenerator gen) throws Exception {
        final boolean forceScalarJit = jitMode == JitMode.SCALAR;
        assertMemoryLeak(() -> {
            if (ddl != null) {
                compiler.compile(ddl, sqlExecutionContext);
            }

            long maxSize = 0;
            List<String> filters = gen.generate();
            LOG.info().$("generated ").$(filters.size()).$(" filter expressions for base query: ").$(baseQuery).$();
            Assert.assertTrue(filters.size() > 0);
            for (String filter : filters) {
                long size = runQuery(baseQuery + " where " + filter, forceScalarJit);
                maxSize = Math.max(maxSize, size);

                TestUtils.assertEquals("result mismatch for filter: " + filter, sink, jitSink);
            }
            Assert.assertTrue(maxSize > 0);
        });
    }

    private void assertQuery(CharSequence query, CharSequence ddl) throws Exception {
        final boolean forceScalarJit = jitMode == JitMode.SCALAR;
        assertMemoryLeak(() -> {
            if (ddl != null) {
                compiler.compile(ddl, sqlExecutionContext);
            }

            runQuery(query, forceScalarJit);

            TestUtils.assertEquals(sink, jitSink);
        });
    }

    private long runQuery(CharSequence query, boolean forceScalarJit) throws SqlException {
        long resultSize;

        sqlExecutionContext.setJitMode(SqlExecutionContext.JIT_MODE_DISABLED);
        CompiledQuery cc = compiler.compile(query, sqlExecutionContext);
        RecordCursorFactory factory = cc.getRecordCursorFactory();
        Assert.assertFalse(factory instanceof CompiledFilterRecordCursorFactory);
        try (CountingRecordCursor cursor = new CountingRecordCursor(factory.getCursor(sqlExecutionContext))) {
            TestUtils.printCursor(cursor, factory.getMetadata(), true, sink, printer);
            resultSize = cursor.count();
        } finally {
            Misc.free(factory);
        }

        int jitMode = forceScalarJit ? SqlExecutionContext.JIT_MODE_FORCE_SCALAR : SqlExecutionContext.JIT_MODE_ENABLED;
        sqlExecutionContext.setJitMode(jitMode);
        cc = compiler.compile(query, sqlExecutionContext);
        factory = cc.getRecordCursorFactory();
        Assert.assertTrue(factory instanceof CompiledFilterRecordCursorFactory);
        try (RecordCursor cursor = factory.getCursor(sqlExecutionContext)) {
            TestUtils.printCursor(cursor, factory.getMetadata(), true, jitSink, printer);
        } finally {
            Misc.free(factory);
        }

        return resultSize;
    }

    private enum JitMode {
        SCALAR, VECTORIZED
    }

    private static class FilterGenerator {

        private static final String[] COMPARISON_OPERATORS = new String[]{" = ", " != ", " > ", " >= ", " < ", " <= "};
        private static final String[] ARITHMETIC_OPERATORS = new String[]{" + ", " - ", " * ", " / "};
        private static final String[] OPTIONAL_NEGATION = new String[]{"", "-"};

        private final List<String[]> filterParts = new ArrayList<>();

        public FilterGenerator withAnyOf(String... parts) {
            filterParts.add(parts);
            return this;
        }

        public FilterGenerator withOptionalNegation() {
            filterParts.add(OPTIONAL_NEGATION);
            return this;
        }

        public FilterGenerator withComparisonOperator() {
            filterParts.add(COMPARISON_OPERATORS);
            return this;
        }

        public FilterGenerator withArithmeticOperator() {
            filterParts.add(ARITHMETIC_OPERATORS);
            return this;
        }

        /**
         * Generates a simple cartesian product of the given filter expression parts.
         *
         * The algorithm originates from Generating All n-tuple, of The Art Of Computer
         * Programming by Knuth.
         */
        public List<String> generate() {
            if (filterParts.size() == 0) {
                return Collections.emptyList();
            }

            int combinations = 1;
            for (String[] parts : filterParts) {
                combinations *= parts.length;
            }

            final List<String> filters = new ArrayList<>();
            final StringBuilder sb = new StringBuilder();

            for (int i = 0; i < combinations; i++) {
                int j = 1;
                for (String[] parts : filterParts) {
                    sb.append(parts[(i / j) % parts.length]);
                    j *= parts.length;
                }
                filters.add(sb.toString());
                sb.setLength(0);
            }
            return filters;
        }
    }

    private static class CountingRecordCursor implements RecordCursor {

        private final RecordCursor delegate;
        private long count;

        public CountingRecordCursor(RecordCursor delegate) {
            this.delegate = delegate;
        }

        public long count() {
            return count;
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public Record getRecord() {
            return delegate.getRecord();
        }

        @Override
        public SymbolTable getSymbolTable(int columnIndex) {
            return delegate.getSymbolTable(columnIndex);
        }

        @Override
        public boolean hasNext() {
            boolean hasNext = delegate.hasNext();
            if (hasNext) {
                count++;
            }
            return hasNext;
        }

        @Override
        public Record getRecordB() {
            return delegate.getRecordB();
        }

        @Override
        public void recordAt(Record record, long atRowId) {
            delegate.recordAt(record, atRowId);
        }

        @Override
        public void toTop() {
            delegate.toTop();
        }

        @Override
        public long size() {
            return delegate.size();
        }
    }

    // TODO: test the following
    // const expr
    // null
    // filter on subquery
    // interval and filter
    // wrong type expression a+b
    // join
    // latest by
}
