import { useState } from "react";

export function App() {
  const [query, setQuery] = useState("");
  return (
    <main>
      <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search notes" />
    </main>
  );
}
