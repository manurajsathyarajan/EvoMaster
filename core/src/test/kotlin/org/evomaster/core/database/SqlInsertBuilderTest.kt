package org.evomaster.core.database

import org.evomaster.clientJava.controller.db.SqlScriptRunner
import org.evomaster.clientJava.controller.internal.db.SchemaExtractor
import org.evomaster.core.search.gene.IntegerGene
import org.evomaster.core.search.gene.StringGene
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager

class SqlInsertBuilderTest {


    companion object {

        private lateinit var connection: Connection

        @BeforeAll
        @JvmStatic
        fun initClass() {
            connection = DriverManager.getConnection("jdbc:h2:mem:db_test", "sa", "")
        }
    }

    @BeforeEach
    fun initTest() {

        //custom H2 command
        SqlScriptRunner.execCommand(connection, "DROP ALL OBJECTS;")
    }


    @Test
    fun testSimpleInt() {

        SqlScriptRunner.execCommand(connection, "CREATE TABLE Foo(x INT);")

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val actions = builder.createSqlInsertionAction("FOO", setOf("X"))

        assertEquals(1, actions.size)

        val genes = actions[0].seeGenes()

        assertEquals(1, genes.size)
        assertTrue(genes[0] is IntegerGene)
    }


    @Test
    fun testColumnSelection() {

        SqlScriptRunner.execCommand(connection, """
                    CREATE TABLE Foo(
                        id bigint generated by default as identity,
                        x INT,
                        y INT not null,
                        z INT
                    );
                """)

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val actions = builder.createSqlInsertionAction("FOO", setOf("X"))

        assertEquals(1, actions.size)

        val genes = actions[0].seeGenes()

        assertTrue(genes.any { it.name.equals("x", ignoreCase = true) })
        assertTrue(genes.any { it.name.equals("y", ignoreCase = true) })

        /*
            - id skipped because auto-incremented value
            - z skipped because nullable and not requested
         */
        assertFalse(genes.any { it.name.equals("id", ignoreCase = true) })
        assertFalse(genes.any { it.name.equals("z", ignoreCase = true) })

        assertEquals(2, genes.size)
        assertTrue(genes[0] is IntegerGene)
        assertTrue(genes[1] is IntegerGene)
    }


    @Test
    fun testVarchar(){

        SqlScriptRunner.execCommand(connection, """
                    CREATE TABLE Foo(
                        x varchar(255),
                        y VARCHAR(128),
                        z varchar
                    );
                """)

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val actions = builder.createSqlInsertionAction("FOO", setOf("X","Y","Z"))

        assertEquals(1, actions.size)

        val genes = actions[0].seeGenes()

        assertEquals(3, genes.size)
        assertTrue(genes[0] is StringGene)
        assertTrue(genes[1] is StringGene)
        assertTrue(genes[2] is StringGene)

        val x = genes.find { it.name.equals("X", ignoreCase = true) } as StringGene
        assertEquals(0, x.minLength)
        assertEquals(255, x.maxLength)

        val y = genes.find { it.name.equals("Y", ignoreCase = true) } as StringGene
        assertEquals(0, y.minLength)
        assertEquals(128, y.maxLength)

        val z = genes.find { it.name.equals("Z", ignoreCase = true) } as StringGene
        assertEquals(0, z.minLength)
        assertEquals(Int.MAX_VALUE, z.maxLength)
    }


    //@Test
    fun testForeignKey(){
        SqlScriptRunner.execCommand(connection, """
            CREATE TABLE Foo(
                id bigint generated by default as identity,
                barId bigint not null,
                primary key (id)
            );
            CREATE TABLE Bar(
                id bigint generated by default as identity,
                primary key (id)
            );
            ALTER TABLE Foo add constraint barIdKey foreign key (barId) references Bar;
        """)

        val dto = SchemaExtractor.extract(connection)

        val builder = SqlInsertBuilder(dto)

        val actions = builder.createSqlInsertionAction("FOO", setOf())

        assertEquals(1, actions.size)

        //TODO
    }
}