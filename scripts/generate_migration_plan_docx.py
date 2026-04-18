#!/usr/bin/env python3
import os
import zipfile
from xml.sax.saxutils import escape


NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"


def cell_color(fill):
    return (
        f'<w:tcPr><w:tcW w:w="2400" w:type="dxa"/>'
        f'<w:shd w:val="clear" w:color="auto" w:fill="{fill}"/></w:tcPr>'
    )


def make_paragraph(text="", style=None, bold=False, align=None, spacing_after=120):
    ppr = []
    if style:
        ppr.append(f'<w:pStyle w:val="{style}"/>')
    if align:
        ppr.append(f'<w:jc w:val="{align}"/>')
    if spacing_after is not None:
        ppr.append(f'<w:spacing w:after="{spacing_after}"/>')
    ppr_xml = f"<w:pPr>{''.join(ppr)}</w:pPr>" if ppr else ""
    rpr = "<w:rPr><w:b/></w:rPr>" if bold else ""
    text_xml = escape(text)
    run = f"<w:r>{rpr}<w:t xml:space=\"preserve\">{text_xml}</w:t></w:r>" if text else ""
    return f"<w:p>{ppr_xml}{run}</w:p>"


def make_bullets(items):
    xml = []
    for item in items:
        xml.append(make_paragraph(f"- {item}", spacing_after=60))
    return "".join(xml)


def make_table(rows, widths, fills=None, header=True):
    tbl_grid = "".join(f'<w:gridCol w:w="{w}"/>' for w in widths)
    tbl = [
        "<w:tbl>",
        (
            "<w:tblPr>"
            "<w:tblStyle w:val=\"TableGrid\"/>"
            "<w:tblW w:w=\"0\" w:type=\"auto\"/>"
            "<w:tblBorders>"
            "<w:top w:val=\"single\" w:sz=\"8\" w:space=\"0\" w:color=\"000000\"/>"
            "<w:left w:val=\"single\" w:sz=\"8\" w:space=\"0\" w:color=\"000000\"/>"
            "<w:bottom w:val=\"single\" w:sz=\"8\" w:space=\"0\" w:color=\"000000\"/>"
            "<w:right w:val=\"single\" w:sz=\"8\" w:space=\"0\" w:color=\"000000\"/>"
            "<w:insideH w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"999999\"/>"
            "<w:insideV w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"999999\"/>"
            "</w:tblBorders>"
            "</w:tblPr>"
        ),
        f"<w:tblGrid>{tbl_grid}</w:tblGrid>",
    ]

    for r_idx, row in enumerate(rows):
        tbl.append("<w:tr>")
        for c_idx, cell in enumerate(row):
            fill = None
            if fills and r_idx < len(fills) and c_idx < len(fills[r_idx]):
                fill = fills[r_idx][c_idx]
            if header and r_idx == 0 and fill is None:
                fill = "D9EAF7"
            tc_pr = cell_color(fill) if fill else '<w:tcPr><w:tcW w:w="2400" w:type="dxa"/></w:tcPr>'
            text = escape(str(cell))
            bold = "<w:rPr><w:b/></w:rPr>" if (header and r_idx == 0) else ""
            tbl.append(
                "<w:tc>"
                f"{tc_pr}"
                "<w:p><w:pPr><w:spacing w:after=\"40\"/></w:pPr>"
                f"<w:r>{bold}<w:t xml:space=\"preserve\">{text}</w:t></w:r>"
                "</w:p></w:tc>"
            )
        tbl.append("</w:tr>")
    tbl.append("</w:tbl>")
    return "".join(tbl)


def document_xml():
    parts = []
    parts.append(make_paragraph("Planificacion de Migracion del Compilador SQL de C++ a Java", style="Title", align="center", spacing_after=180))
    parts.append(make_paragraph("Proyecto academico - Compiladores 1", style="Subtitle", align="center", spacing_after=80))
    parts.append(make_paragraph("Universidad - Entrega final: 30 de mayo de 2026", align="center", spacing_after=80))
    parts.append(make_paragraph("Equipo de trabajo: 4 integrantes", align="center", spacing_after=240))

    parts.append(make_paragraph("1. Proposito del documento", style="Heading1"))
    parts.append(make_paragraph(
        "Este documento presenta la planificacion para migrar el compilador SQL desarrollado en C++ hacia Java. "
        "La propuesta esta adaptada a un proyecto universitario con un equipo de 4 integrantes y una fecha limite "
        "de entrega el 30 de mayo de 2026."
    ))

    parts.append(make_paragraph("2. Contexto del proyecto", style="Heading1"))
    parts.append(make_paragraph(
        "El proyecto actual implementa un compilador SQL academico con las fases de analisis lexico, analisis "
        "sintactico y analisis semantico. El codigo fuente base esta en C++ y el objetivo de la migracion es "
        "reconstruir esas mismas fases en Java, manteniendo el comportamiento principal del compilador."
    ))
    parts.append(make_paragraph("Alcance real considerado para la migracion:", spacing_after=60))
    parts.append(make_bullets([
        "Tokenizacion de consultas SQL simples.",
        "Construccion del AST para sentencias SELECT con FROM y WHERE.",
        "Validacion semantica basica usando tabla de simbolos.",
        "Pruebas de equivalencia entre la version en C++ y la version en Java.",
    ]))

    parts.append(make_paragraph("3. Objetivo general", style="Heading1"))
    parts.append(make_paragraph(
        "Migrar el compilador SQL base desde C++ a Java antes del 30 de mayo de 2026, obteniendo una version "
        "funcional, documentada y demostrable en clase."
    ))

    parts.append(make_paragraph("4. Objetivos especificos", style="Heading1"))
    parts.append(make_bullets([
        "Analizar la arquitectura actual del compilador en C++.",
        "Disenar la estructura equivalente del proyecto en Java.",
        "Implementar en Java el lexer, el parser, el AST y el analizador semantico.",
        "Construir pruebas de validacion para comparar resultados.",
        "Preparar la documentacion y la presentacion final del proyecto.",
    ]))

    parts.append(make_paragraph("5. Supuestos de trabajo", style="Heading1"))
    parts.append(make_bullets([
        "La fecha de inicio planificada es el 20 de abril de 2026.",
        "Cada integrante dedicara en promedio 8 horas por semana.",
        "La migracion se enfocara en el alcance real del proyecto academico actual y no en una expansion completa de SQL.",
        "La integracion final se entregara como proyecto Java ejecutable con evidencias de pruebas.",
    ]))

    parts.append(make_paragraph("6. Fases del proyecto", style="Heading1"))
    phase_rows = [
        ["Fase", "Periodo", "Objetivo principal", "Entregable"],
        ["Fase 1", "20 al 25 abril", "Analisis del sistema C++ y definicion del alcance", "Inventario tecnico y backlog"],
        ["Fase 2", "27 abril al 2 mayo", "Diseno de arquitectura Java y configuracion del proyecto", "Base del proyecto Java"],
        ["Fase 3", "4 al 9 mayo", "Migracion del lexer y definicion de tokens", "Lexer funcional en Java"],
        ["Fase 4", "11 al 16 mayo", "Migracion del AST y parser", "Parser funcional en Java"],
        ["Fase 5", "18 al 23 mayo", "Migracion de semantica, tabla de simbolos e integracion", "Compilador Java integrado"],
        ["Fase 6", "25 al 30 mayo", "Pruebas, correcciones, informe y exposicion", "Entrega final"],
    ]
    parts.append(make_table(phase_rows, [1800, 2100, 4200, 2400]))
    parts.append(make_paragraph("", spacing_after=120))

    parts.append(make_paragraph("7. Diagrama de Gantt", style="Heading1"))
    parts.append(make_paragraph(
        "El siguiente Gantt resume las actividades distribuidas en 6 semanas. Las celdas sombreadas indican el periodo de ejecucion."
    ))
    gantt_rows = [
        ["Actividad", "S1\n20-25 abr", "S2\n27 abr-2 may", "S3\n4-9 may", "S4\n11-16 may", "S5\n18-23 may", "S6\n25-30 may"],
        ["Analisis del codigo C++", "X", "", "", "", "", ""],
        ["Levantamiento de requisitos y alcance", "X", "X", "", "", "", ""],
        ["Diseno de arquitectura Java", "", "X", "", "", "", ""],
        ["Configuracion del proyecto Java", "", "X", "", "", "", ""],
        ["Migracion de tokens y lexer", "", "", "X", "", "", ""],
        ["Pruebas del lexer", "", "", "X", "X", "", ""],
        ["Migracion de AST y parser", "", "", "", "X", "", ""],
        ["Pruebas sintacticas", "", "", "", "X", "X", ""],
        ["Migracion de tabla de simbolos", "", "", "", "", "X", ""],
        ["Migracion semantica e integracion", "", "", "", "", "X", ""],
        ["Pruebas finales y correcciones", "", "", "", "", "X", "X"],
        ["Informe, diapositivas y ensayo", "", "", "", "", "", "X"],
    ]
    gantt_fills = []
    for r_idx, row in enumerate(gantt_rows):
        fill_row = []
        for c_idx, cell in enumerate(row):
            if r_idx == 0:
                fill_row.append("D9EAF7")
            elif c_idx == 0:
                fill_row.append(None)
            elif cell == "X":
                fill_row.append("92D050")
            else:
                fill_row.append("FFFFFF")
        gantt_fills.append(fill_row)
    parts.append(make_table(gantt_rows, [3600, 1200, 1200, 1200, 1200, 1200, 1200], fills=gantt_fills))
    parts.append(make_paragraph("Leyenda: verde = actividad planificada en la semana correspondiente.", spacing_after=180))

    parts.append(make_paragraph("8. Asignacion de tareas por integrante", style="Heading1"))
    assign_rows = [
        ["Integrante", "Rol principal", "Responsabilidades", "Tiempo estimado"],
        ["Integrante 1", "Coordinacion y arquitectura", "Planificacion, diseno general, apoyo en parser, integracion final", "48 horas"],
        ["Integrante 2", "Desarrollo de frontend del compilador", "Tokens, lexer, casos de prueba lexicos, documentacion tecnica", "48 horas"],
        ["Integrante 3", "Desarrollo del parser", "AST, parser, validaciones sintacticas, correccion de errores", "48 horas"],
        ["Integrante 4", "Semantica, QA y entrega", "Tabla de simbolos, analisis semantico, pruebas integrales, informe y exposicion", "48 horas"],
    ]
    parts.append(make_table(assign_rows, [1700, 2400, 4300, 1600]))
    parts.append(make_paragraph("", spacing_after=100))

    parts.append(make_paragraph("9. Distribucion semanal por integrante", style="Heading1"))
    weekly_rows = [
        ["Integrante", "S1", "S2", "S3", "S4", "S5", "S6", "Total"],
        ["Integrante 1", "8h", "8h", "8h", "8h", "8h", "8h", "48h"],
        ["Integrante 2", "8h", "8h", "8h", "8h", "8h", "8h", "48h"],
        ["Integrante 3", "8h", "8h", "8h", "8h", "8h", "8h", "48h"],
        ["Integrante 4", "8h", "8h", "8h", "8h", "8h", "8h", "48h"],
        ["Total equipo", "32h", "32h", "32h", "32h", "32h", "32h", "192h"],
    ]
    parts.append(make_table(weekly_rows, [1800, 900, 900, 900, 900, 900, 900, 1300]))

    parts.append(make_paragraph("10. Riesgos y acciones de mitigacion", style="Heading1"))
    risk_rows = [
        ["Riesgo", "Impacto", "Mitigacion"],
        ["Diferencias entre el codigo C++ y la version Java", "Medio", "Comparar salidas con casos de prueba comunes desde la semana 3."],
        ["Falta de tiempo en semanas finales", "Alto", "Reservar la semana 6 para correcciones y no para desarrollo nuevo."],
        ["Errores de integracion entre modulos", "Alto", "Hacer integraciones parciales desde la semana 4 y no esperar al final."],
        ["Carga desigual entre integrantes", "Medio", "Definir avances semanales y revisiones cruzadas cada sabado."],
    ]
    parts.append(make_table(risk_rows, [2600, 1200, 4200]))

    parts.append(make_paragraph("11. Entregables finales", style="Heading1"))
    parts.append(make_bullets([
        "Codigo fuente del compilador migrado a Java.",
        "Casos de prueba y evidencia de ejecucion.",
        "Informe escrito con descripcion de la migracion.",
        "Diapositivas para la exposicion final.",
        "Demostracion funcional del compilador en Java.",
    ]))

    parts.append(make_paragraph("12. Conclusion", style="Heading1"))
    parts.append(make_paragraph(
        "Con una planificacion de 6 semanas, una dedicacion promedio de 8 horas por integrante y una division clara "
        "de responsabilidades, el proyecto es viable para ser entregado el 30 de mayo de 2026. La clave del exito "
        "sera mantener un alcance controlado, validar constantemente contra la version en C++ y reservar tiempo final "
        "para pruebas y documentacion."
    ))

    body = "".join(parts)
    return (
        f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        f'<w:document xmlns:w="{NS_W}">'
        f'<w:body>{body}<w:sectPr>'
        '<w:pgSz w:w="12240" w:h="15840"/>'
        '<w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/>'
        '</w:sectPr></w:body></w:document>'
    )


def content_types_xml():
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>
"""


def rels_xml():
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>
"""


def document_rels_xml():
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>
"""


def styles_xml():
    return f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="{NS_W}">
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:qFormat/>
    <w:rPr>
      <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
      <w:sz w:val="22"/>
    </w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Title">
    <w:name w:val="Title"/>
    <w:basedOn w:val="Normal"/>
    <w:qFormat/>
    <w:pPr><w:spacing w:after="220"/></w:pPr>
    <w:rPr>
      <w:b/>
      <w:sz w:val="34"/>
      <w:color w:val="1F4E78"/>
    </w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Subtitle">
    <w:name w:val="Subtitle"/>
    <w:basedOn w:val="Normal"/>
    <w:qFormat/>
    <w:rPr>
      <w:sz w:val="24"/>
      <w:color w:val="4F81BD"/>
    </w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:basedOn w:val="Normal"/>
    <w:uiPriority w:val="9"/>
    <w:qFormat/>
    <w:pPr><w:spacing w:before="220" w:after="100"/></w:pPr>
    <w:rPr>
      <w:b/>
      <w:sz w:val="28"/>
      <w:color w:val="1F1F1F"/>
    </w:rPr>
  </w:style>
</w:styles>
"""


def core_xml():
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
 xmlns:dc="http://purl.org/dc/elements/1.1/"
 xmlns:dcterms="http://purl.org/dc/terms/"
 xmlns:dcmitype="http://purl.org/dc/dcmitype/"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:title>Planificacion de Migracion del Compilador SQL de C++ a Java</dc:title>
  <dc:creator>Codex</dc:creator>
  <cp:lastModifiedBy>Codex</cp:lastModifiedBy>
  <dcterms:created xsi:type="dcterms:W3CDTF">2026-04-18T15:00:00Z</dcterms:created>
  <dcterms:modified xsi:type="dcterms:W3CDTF">2026-04-18T15:00:00Z</dcterms:modified>
</cp:coreProperties>
"""


def app_xml():
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"
 xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
  <Application>Microsoft Office Word</Application>
  <DocSecurity>0</DocSecurity>
  <ScaleCrop>false</ScaleCrop>
  <Company>Universidad</Company>
  <LinksUpToDate>false</LinksUpToDate>
  <SharedDoc>false</SharedDoc>
  <HyperlinksChanged>false</HyperlinksChanged>
  <AppVersion>16.0000</AppVersion>
</Properties>
"""


def write_docx(output_path):
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with zipfile.ZipFile(output_path, "w", compression=zipfile.ZIP_DEFLATED) as docx:
        docx.writestr("[Content_Types].xml", content_types_xml())
        docx.writestr("_rels/.rels", rels_xml())
        docx.writestr("word/document.xml", document_xml())
        docx.writestr("word/_rels/document.xml.rels", document_rels_xml())
        docx.writestr("word/styles.xml", styles_xml())
        docx.writestr("docProps/core.xml", core_xml())
        docx.writestr("docProps/app.xml", app_xml())


if __name__ == "__main__":
    output = os.path.join(
        os.getcwd(),
        "output",
        "doc",
        "Planificacion_Migracion_Compilador_SQL_Cpp_a_Java.docx",
    )
    write_docx(output)
    print(output)
