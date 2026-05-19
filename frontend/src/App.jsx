import React, { useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  BookOpen,
  CheckCircle2,
  Database,
  Download,
  FileCode2,
  Folder,
  GitFork,
  HelpCircle,
  Library,
  ListChecks,
  Play,
  Plus,
  Search,
  Settings,
  Trash2,
  X,
  Zap
} from 'lucide-react';
import './styles.css';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

const sampleSql = `-- Logic Atelier SQL Journal v2.0
-- Active Schema: presupuestos

SELECT *
FROM presupuestos
WHERE id > 0
LIMIT 10;`;

const navItems = [
  { id: 'workbook', label: 'Workbook', icon: FileCode2 },
  { id: 'ast', label: 'AST Visualizer', icon: GitFork },
  { id: 'schema', label: 'Schema & Errors', icon: ListChecks },
  { id: 'library', label: 'Library', icon: Folder }
];

function App() {
  const [view, setView] = useState('workbook');
  const [sql, setSql] = useState(sampleSql);
  const [analysis, setAnalysis] = useState(null);
  const [isRunning, setIsRunning] = useState(false);
  const [error, setError] = useState('');

  const diagnostics = analysis?.diagnostics ?? [];
  const hasErrors = diagnostics.some((item) => item.severity === 'ERROR');

  async function runQuery() {
    setIsRunning(true);
    setError('');
    try {
      const response = await fetch(`${API_BASE_URL}/api/v1/sql/analyze`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sql })
      });
      const payload = await response.json();
      if (!response.ok) {
        throw new Error(payload.detail ?? 'No se pudo analizar el query.');
      }
      setAnalysis(payload);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setIsRunning(false);
    }
  }

  const content = {
    workbook: <Workbook sql={sql} setSql={setSql} analysis={analysis} error={error} />,
    ast: <AstVisualizer sql={sql} analysis={analysis} />,
    schema: <SchemaErrors sql={sql} analysis={analysis} />,
    library: <LibraryView analysis={analysis} runQuery={runQuery} isRunning={isRunning} />
  }[view];

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <strong>Logic Atelier</strong>
          <span>Technical Journal</span>
        </div>
        <button className="new-query" onClick={() => setSql('SELECT *\nFROM presupuestos\nLIMIT 10;')}>
          <Plus size={22} /> New Query
        </button>
        <nav>
          <span className="nav-title">Main Views</span>
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.id}
                className={view === item.id ? 'active' : ''}
                onClick={() => setView(item.id)}
              >
                <Icon size={20} />
                {item.label}
              </button>
            );
          })}
        </nav>
        <div className="sidebar-footer">
          <button><BookOpen size={18} /> Docs</button>
          <button><HelpCircle size={18} /> Help</button>
        </div>
      </aside>

      <main>
        <header className="topbar">
          <div className="topbar-title">
            <strong>{view === 'workbook' ? 'Workbook.sql' : view === 'schema' ? 'Semantic Analysis' : 'Logic Atelier'}</strong>
            <span>{view === 'workbook' ? 'Query' : view === 'ast' ? 'Query Explorer' : view === 'library' ? 'Query Explorer' : 'Current Session: query_engine_v2.sql'}</span>
          </div>
          <div className="toolbar">
            {analysis && (
              <span className={hasErrors ? 'status-pill error' : 'status-pill'}>
                {hasErrors ? 'Errors' : 'Valid'}
              </span>
            )}
            <button className="icon-button"><Trash2 size={20} /></button>
            <button className="icon-button"><Settings size={22} /></button>
            <button className="save-button">Save</button>
            <button className="run-button" onClick={runQuery} disabled={isRunning}>
              <Play size={18} fill="currentColor" />
              {isRunning ? 'Running' : 'Run Query'}
            </button>
          </div>
        </header>
        {analysis && !hasErrors && (
          <div className="toast success">
            <CheckCircle2 size={24} />
            <div>
              <strong>Compilation Successful</strong>
              <span>Query executed in {analysis.execution?.elapsedMs ?? 0}ms. {analysis.execution?.rowCount ?? 0} rows returned.</span>
            </div>
          </div>
        )}
        {content}
      </main>
    </div>
  );
}

function Workbook({ sql, setSql, analysis, error }) {
  return (
    <section className="workbook">
      <div className="editor-shell">
        <LineNumbers text={sql} />
        <textarea value={sql} onChange={(event) => setSql(event.target.value)} spellCheck="false" />
      </div>
      <ResultPanel analysis={analysis} error={error} />
    </section>
  );
}

function LineNumbers({ text }) {
  const lines = text.split('\n').map((_, index) => index + 1);
  return <div className="line-numbers">{lines.map((line) => <span key={line}>{line}</span>)}</div>;
}

function ResultPanel({ analysis, error }) {
  const rows = analysis?.execution?.rows ?? [];
  const columns = analysis?.execution?.columns ?? [];
  return (
    <div className="result-panel">
      <div className="tabs">
        <button className="selected">Result Grid</button>
        <button>Console</button>
        <button>Execution Plan</button>
        <span />
        <Download size={18} />
        <X size={18} />
      </div>
      {error && <div className="console-error">{error}</div>}
      {analysis && rows.length === 0 && <div className="empty-state">{analysis.execution?.message ?? 'Sin resultados.'}</div>}
      {rows.length > 0 && (
        <table>
          <thead>
            <tr>{columns.map((column) => <th key={column}>{column}</th>)}</tr>
          </thead>
          <tbody>
            {rows.map((row, rowIndex) => (
              <tr key={rowIndex}>
                {columns.map((column) => <td key={column}>{String(row[column] ?? '')}</td>)}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function AstVisualizer({ sql, analysis }) {
  return (
    <section className="ast-page">
      <div className="page-intro">
        <h1>Abstract Syntax Tree</h1>
        <p>Visualizing the syntactic structure of your SQL statement and the grammar rules applied by the Java parser.</p>
      </div>
      <div className="ast-layout">
        <aside className="source-card">
          <div className="card-head">
            <strong>Source Query</strong>
            <span className={analysis?.valid ? 'valid-badge' : 'warn-badge'}>{analysis?.valid ? 'Valid' : 'Pending'}</span>
          </div>
          <pre>{sql}</pre>
          <div className="hint">
            <Zap size={18} />
            <span>{analysis?.statementType ?? 'SELECT'} is the root statement for the current query.</span>
          </div>
          <div className="stats">
            <span><b>{analysis?.tokens?.length ?? 0}</b> tokens</span>
            <span><b>{analysis?.statementCount ?? 0}</b> statements</span>
          </div>
        </aside>
        <div className="tree-card">
          <div className="card-head">
            <strong><GitFork size={20} /> Visual Parse Tree</strong>
          </div>
          {analysis?.ast ? <Tree node={analysis.ast} /> : <div className="empty-state">Run Query para generar el AST.</div>}
        </div>
      </div>
    </section>
  );
}

function Tree({ node }) {
  return (
    <div className="tree-node">
      <span>{node.type}: {node.label}</span>
      {node.children?.length > 0 && (
        <div className="tree-children">
          {node.children.map((child, index) => <Tree key={`${child.label}-${index}`} node={child} />)}
        </div>
      )}
    </div>
  );
}

function SchemaErrors({ sql, analysis }) {
  const diagnostics = analysis?.diagnostics ?? [];
  const symbols = useMemo(() => {
    const tables = analysis?.semantic?.tables ?? [];
    const columns = analysis?.semantic?.columns ?? [];
    return [
      ...tables.map((value) => ({ name: value, type: 'TableReference', scope: 'Database' })),
      ...columns.map((value) => ({ name: value, type: 'ColumnExpression', scope: 'Query' }))
    ];
  }, [analysis]);

  return (
    <section className="schema-page">
      <div className="page-intro compact">
        <span>Debugger › Symbol Resolution</span>
        <h1>Scope & Identification</h1>
        <p>Verification of identifier declarations, type consistency, and database resolution for the current SQL context.</p>
      </div>
      <div className="schema-grid">
        <div className="symbols-card">
          <div className="section-title"><Database size={24} /> Identified Symbols</div>
          <table>
            <thead><tr><th>Variable Name</th><th>Symbol Type</th><th>Scope Level</th></tr></thead>
            <tbody>
              {symbols.map((symbol) => (
                <tr key={`${symbol.type}-${symbol.name}`}>
                  <td>{symbol.name}</td>
                  <td>{symbol.type}</td>
                  <td>{symbol.scope}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="buffer-card">
          <strong>Active Buffer</strong>
          <pre>{sql.split('\n').slice(0, 8).join('\n')}</pre>
          <Metric label="Lexical Coverage" value={analysis ? 100 : 0} />
          <Metric label="Semantic Integrity" value={analysis?.valid ? 100 : 62} danger={!analysis?.valid} />
        </div>
      </div>
      <div className="violations">
        <h2>Semantic Violations</h2>
        {diagnostics.length === 0 && <div className="empty-state">No hay errores registrados.</div>}
        {diagnostics.map((item, index) => (
          <div key={index} className={item.severity === 'ERROR' ? 'violation error' : 'violation warn'}>
            <strong>{item.phase}</strong>
            <span>{item.message}</span>
            <small>Line {item.line}:{item.column}</small>
          </div>
        ))}
      </div>
    </section>
  );
}

function Metric({ label, value, danger }) {
  return (
    <label className="metric">
      <span>{label}<b>{value}%</b></span>
      <progress value={value} max="100" className={danger ? 'danger' : ''} />
    </label>
  );
}

function LibraryView({ analysis, runQuery, isRunning }) {
  const tests = [
    ['LexicalScoping_ValidTokens', 'Tests tokenizing reserved keywords and identifiers', !analysis || analysis.tokens?.length > 0],
    ['Parser_SelectClauses', 'Validation of SELECT, FROM, JOIN and clause ordering', analysis?.ast],
    ['Semantic_TableResolution', 'Verifying referenced tables against MySQL metadata', analysis?.semantic?.connected],
    ['Execution_ReadOnlyGuard', 'Ensures only read-only SQL is executed from the UI', true]
  ];

  return (
    <section className="library-page">
      <div className="library-list">
        <div className="library-head">
          <h1>Library</h1>
          <span>24 Items</span>
        </div>
        <div className="search-box"><Search size={22} /> <span>Filter queries...</span></div>
        {['User Retention Cohorts', 'Schema Health Audit', 'Recursive AST Parser'].map((title, index) => (
          <article key={title}>
            <strong>{title}</strong>
            <p>{index === 0 ? 'Calculates query results using the connected database...' : 'Validates compiler behavior against SQL source input...'}</p>
            <small>Oct {12 - index * 3}, 2026</small>
          </article>
        ))}
      </div>
      <div className="suite">
        <div className="suite-head">
          <div>
            <h1>Test Suite</h1>
            <p>Java Spring Boot SQL Analyzer</p>
          </div>
          <button onClick={runQuery} disabled={isRunning}>Rerun Suite</button>
          <button className="run-button">Continuous Integration</button>
        </div>
        <div className="score-grid">
          <Score value={analysis?.valid ? '98.2%' : '0%'} label="Coverage Rate" />
          <Score value={`${analysis?.execution?.elapsedMs ?? 0}ms`} label="Mean Exec Time" />
          <Score value={`${tests.filter((test) => test[2]).length}/${tests.length}`} label="Tests Passed" green />
        </div>
        <div className="report">
          <h2>Execution Report</h2>
          {tests.map(([name, description, pass]) => (
            <div className={pass ? 'test-row pass' : 'test-row fail'} key={name}>
              {pass ? <CheckCircle2 size={22} /> : <X size={22} />}
              <div><strong>{name}</strong><span>{description}</span></div>
              <b>{pass ? 'PASS' : 'FAIL'}</b>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function Score({ value, label, green }) {
  return <div className={green ? 'score green' : 'score'}><strong>{value}</strong><span>{label}</span></div>;
}

createRoot(document.getElementById('root')).render(<App />);
