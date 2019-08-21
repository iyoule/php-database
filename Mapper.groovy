import com.intellij.database.model.DasObject
import com.intellij.database.model.DasTable
import com.intellij.database.model.DasColumn
import com.intellij.database.model.DasTableKey
import com.intellij.database.util.Case
import com.intellij.database.util.DasUtil
import com.intellij.database.psi.DbColumnImpl

import java.lang.reflect.Array

/*
 * Available context bindings:
 *   SELECTION   Iterable<DasObject>
 *   PROJECT     project
 *   FILES       files helper
 */

packageName = "App\\Mapper;"

classSuffix = "Mapper"

typeMapping = [
        (~/(?i)int/)                      : "int",
        (~/(?i)float|double|decimal|real/): "float",
        (~/(?i)enum\('0', '1'\)/)         : "string",
        (~/(?i)enum\('1', '0'\)/)         : "string",
        (~/(?i)/)                         : "string"
]

FILES.chooseDirectoryAndSave("Choose directory", "Choose where to store generated files") { dir ->
    SELECTION.filter { it instanceof DasTable }.each { generate(it, dir) }
}

def generate(table, dir) {
    def className = javaName(table.getName(), true)
    def fields = calcFields(table)
    new File(dir, className + "${classSuffix}.php").withPrintWriter { out -> generate(out, table.getName(), className, fields) }
}

def generate(out, table, className, fields) {

    DasColumn column
    DasTable oTable

    out.println "<?php"
    out.println ""
    out.println "namespace $packageName"
    out.println ""
    out.println "use \\iyoule\\Database\\Eloquent\\Mapper;"
    out.println ""
    out.println "/**"
    out.println " * class ${className}${classSuffix}"
    fields.each() {
        out.println " * @property ${it.type} \$${it.col} ${it.cmt}"
    }
    out.println " * @package ${packageName}"
    out.println " * @mixin \\Eloquent"
    out.println " */"
    out.println "class ${className}${classSuffix} extends Mapper {"
    out.println ""
    out.println "    protected \$table=\"$table\";"
    def i = 0;

    ArrayList<String> fillable = new ArrayList<>()
    ArrayList<String> guarded = new ArrayList<>()
    ArrayList<String> primaryKey = new ArrayList<>()
    ArrayList<String> updatedList = new ArrayList<>()
    ArrayList<String> createdList = new ArrayList<>()

    boolean isIncr = true;
    boolean incrType = "int";

    fields.each() {
        column = it.source
        oTable = it.table
        col = "'" + it.col + "'"
        Set<DasColumn.Attribute> attributes = oTable.getColumnAttrs(column)
        if (attributes.contains(DasColumn.Attribute.PRIMARY_KEY)) {
            primaryKey.add(it.col)
            guarded.add(col)
            isIncr = attributes.contains(DasColumn.Attribute.AUTO_GENERATED)
            incrType = it.type
        } else {
            fillable.add(col)
        }

        if (column.getName().indexOf("update") > 0 && column.getDataType().toString().toLowerCase().indexOf("int")) {
            updatedList.add(column.getName())
        } else if (column.getName().indexOf("create") > 0 && column.getDataType().toString().toLowerCase().indexOf("int")) {
            createdList.add(column.getName())
        }

    }

    if (false == isIncr) {
        out.println ""
        out.println "    public \$incrementing = false;"

        if (!"int".equals(incrType)) {
            out.println ""
            out.println "    protected \$keyType = 'string';"
        }

        for (int ni = 0; ni < primaryKey.size(); ni++) {
            for (int j = 0; j < guarded.size(); j++) {
                if (guarded.get(j).equals("'"+primaryKey.get(ni)+"'")) {
                    fillable.add(guarded.get(j))
                    guarded.remove(j)
                }
            }
        }

    }

    for (int j = 0; j < primaryKey.size(); j++) {
        out.println ""
        out.println "    protected \$primaryKey = '${primaryKey.get(j)}'; "
    }

    out.println ""
    out.println "    protected \$fillable = ${fillable};"
    out.println ""
    out.println "    protected \$guarded = ${guarded}; "
    out.println ""


    if (updatedList.size() == 0 && createdList.size() == 0) {
        out.println "    public \$timestamps = false;"
        out.println ""
    } else {
        if (updatedList.size() == 0) {
            out.println "    const UPDATED_AT = null;"
            out.println ""
        } else {
            out.println "    const UPDATED_AT = '${updatedList.get(0)}';"
            out.println ""
        }
        if (createdList.size() == 0) {
            out.println "    const CREATED_AT = null;"
            out.println ""
        } else {
            out.println "    const CREATED_AT = '${updatedList.get(0)}';"
            out.println ""
        }
    }


    fields.each() {
        column = it.source
        if (column.getDataType().enumValues != null) {
            ArrayList<String> enumValues = new ArrayList<>();
            for (int j = 0; j < column.getDataType().enumValues.size(); j++) {
                String x = column.getDataType().enumValues.get(j)
                enumValues.add(x.replace("'", "").replace("\"", ""))
            }


            boolean isBool = false;
            if (enumValues.size() == 2) {
                if (enumValues.get(0) == "1" && enumValues.get(1) == "0") {
                    isBool = true;
                } else if (enumValues.get(0) == "0" && enumValues.get(1) == "1") {
                    isBool = true;
                }
            }

            String constname = it.col.toString().toUpperCase()

            if (isBool) {
                out.println("    const ${constname}_YES = \"1\";")
                out.println("    const ${constname}_ON = \"0\";")
            } else {
                for (int j = 0; j < enumValues.size(); j++) {
                    String tmp = enumValues.get(j)
                    String nametmp = constname + "_" + tmp.toUpperCase()
                    out.println("    const ${nametmp} = \"${tmp}\";")
                }
            }
        }
    }
    out.println ""
    out.println "}"
}


def calcFields(DasObject table) {
    DasUtil.getColumns(table).reduce([]) { fields, col ->

        def spec = Case.LOWER.apply(col.getDataType().getSpecification())
        def typeStr = typeMapping.find { p, t -> p.matcher(spec).find() }.value
        fields += [[
                           source: col,
                           name  : javaName(col.getName(), false),
                           col   : col.getName(),
                           cmt   : col.getComment(),
                           type  : typeStr,
                           table : table,
                           annos : ""]]
    }
}

def javaName(str, capitalize) {
    def s = com.intellij.psi.codeStyle.NameUtil.splitNameIntoWords(str)
            .collect { Case.LOWER.apply(it).capitalize() }
            .join("")
            .replaceAll(/[^\p{javaJavaIdentifierPart}[_]]/, "_")
    capitalize || s.length() == 1 ? s : Case.LOWER.apply(s[0]) + s[1..-1]
}
