# Nastech Skills, Tools, and Safe Update Findings

## Curated capability sources

A large instruction-library source is [VoltAgent’s Awesome Agent Skills](https://github.com/VoltAgent/awesome-agent-skills), an MIT-licensed index of official and community skill packages. It includes content-oriented skills such as document, spreadsheet, PDF, frontend, testing, design, and MCP-builder guidance. It is an index, not a safe bulk-import target: every source skill must have its licence, required runtime, network behavior, and credentials evaluated before it becomes available in Nastech.[1]

[Softaworks Agent Toolkit](https://github.com/softaworks/agent-toolkit) is MIT-licensed and uses a familiar per-skill structure of `SKILL.md`, user documentation, optional helper scripts, and references. Its coding, documentation, planning, testing, diagram, and data-model skills are useful candidates for the first curated Nastech catalogue, provided that command-line or provider-bound entries are marked as requiring an external runtime or user-provided connection.[2]

The official [Model Context Protocol reference repository](https://github.com/modelcontextprotocol/servers) supplies examples for fetch, filesystem, git, memory, sequential-thinking, and time. Its maintainers describe the reference servers as educational examples rather than production-ready services, so they should never be bulk-enabled in Nastech. Nastech should connect them only through its existing MCP configuration and approval screens, keeping filesystem access scoped and credentials explicitly user-supplied.[3]

## Catalogue policy

The requested 330 skills and 229 tools should be delivered as a **catalogue and connection framework**, not hundreds of false switches. A skill is an instruction/resource bundle, while a tool is an executable capability. Nastech should show each item’s source, licence, category, requirement state, permission surface, and availability. Only on-device or already-integrated capabilities can be enabled immediately. Remote services, shells, file access, accounts, and payments must retain existing user approval and connection flows.

| Capability tier | Nastech behavior | Examples |
|---|---|---|
| Built in | Available through the assistant’s existing local-tool configuration and approvals. | File preparation, diagrams, chart instructions, image generation route, chat storage, voice reading. |
| Guided skill | Imported as instruction/resource content after licence review; no untrusted script automatically runs. | Kotlin/Android patterns, React patterns, Mermaid, spreadsheet workflow, QA planning. |
| Connected tool | Appears in the catalogue but must be attached through an existing provider, MCP, or user credential configuration. | Git hosting, web research provider, cloud storage, issue tracker, database. |
| Unsupported / review | Visible only to maintainers or omitted until a safe runtime and permission boundary exists. | Arbitrary shell executors, browser automation from unreviewed third parties, tools requiring ambient account access. |

## Update flow safety

Android’s documentation notes security concerns with unmanaged downloads and recommends direct app-controlled downloads or carefully scoped storage rather than broadly writable public paths. Nastech’s updater therefore constrains update assets to HTTPS APK URLs, stores them under app-owned external files, tracks only the initiated download identifier, and opens Android’s package installer only after a user action. Android remains responsible for displaying the install confirmation and verifying the signing certificate; Nastech must not silently install software.[4]

## References

[1]: https://github.com/VoltAgent/awesome-agent-skills "VoltAgent Awesome Agent Skills"
[2]: https://github.com/softaworks/agent-toolkit "Softaworks Agent Toolkit"
[3]: https://github.com/modelcontextprotocol/servers "Model Context Protocol reference servers"
[4]: https://developer.android.com/privacy-and-security/risks/unsafe-download-manager "Android Developers: Unsafe Download Manager"
