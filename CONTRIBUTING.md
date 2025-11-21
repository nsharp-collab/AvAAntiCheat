📝 Contributing to AvAAntiCheat

Thank you for your interest in contributing to AvAAntiCheat! Your effort helps keep Minecraft servers fair and fun.

By contributing, you agree that your submissions are licensed under the Apache License, Version 2.0 (AL 2.0), the same license that governs this project.

🤝 How to Contribute

We welcome contributions in four main areas: New Checks, Bug Fixes, Documentation, and Other Changes.

1. New Checks (Detecting Cheats)

The most valuable contributions are new and reliable cheat detection modules (often called "Checks").

Goal: New checks must be robust, performant, and have an extremely low rate of false positives (legitimate players being flagged).

Structure: All new checks should follow the existing organizational pattern within the com.nolan.ava.check package.

⚠️ Testing Requirement (Heavily Encouraged): New checks must be thoroughly tested against known cheating clients to ensure detection, and against legitimate, high-latency, or highly skilled players to ensure accuracy. If you are unable to perform extensive testing, you must leave a prominent note in the Pull Request description stating that the check is untested and requires internal validation.

Configuration: New checks should include sensible default settings and, ideally, be toggleable via the main configuration file.

2. Bug Reports and Code Fixes

If you find a bug in an existing check (high false-positive rate, a bypass, or server performance issue), please submit an Issue first.

Issues: Provide a detailed description, steps to reproduce the issue, and, if applicable, the server version and any error logs.

Fixes: If you submit a fix via a Pull Request (PR), reference the related Issue number (e.g., "Fixes #123").

3. Documentation and Translations

Improvements to the README.md, translation files, or internal code comments are always welcome.

4. Other Changes (Core, Config, Logging)

This category covers changes to core systems that are not specific cheat checks, such as:

Improvements to the logging system (e.g., performance, clarity of log messages).

Changes to the main configuration structure or command handlers.

Optimizations or refactors to the main plugin loop or core anti-cheat logic.

⚙️ Contribution Workflow (Submitting Code)

Fork the Repository: Create your own fork of the official AvAAntiCheat repository.

Create a Branch: Create a new branch for your feature or fix (e.g., feature/fly-check-v3 or fix/pvp-logging).

Make Your Changes: Write your code, following the existing style and conventions.

⚠️ Adhering to the Apache License 2.0

This is a critical step for all contributors to ensure legal clarity regarding patents and copyright.

1. License Header (Mandatory) for New Files

When you create a brand-new source file:

You must include the full Apache 2.0 license boilerplate header at the top of the file, replacing the placeholder values ([yyyy] and [name of copyright owner]) with your copyright information. This makes you the primary copyright holder and licensor for that file.

Example for a new file in 2025:
```
/*
 * Copyright 2025 Your Name or Company Name
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * [http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```

2. Modification Notices (For Existing Files)

If you modify an existing file created by someone else (or the original owner, Nolan Sharp), you must add a "prominent notice" stating that you changed the file, directly below the original copyright header.

Example:
```
/*
 * Copyright 2025 Nolan Sharp
 * ... (rest of Nolan Sharp's license header) ...
 *
 * -------------------------------------------------------------
 * **MODIFIED by Your Name on 2025-11-20 to optimize movement checks.**
 * -------------------------------------------------------------
 */
```

3. The NOTICE File

You are not required to create a NOTICE file unless your contribution incorporates code that explicitly requires external attribution (e.g., another project that requires you to list their name in a NOTICE file).

🚀 Submitting Your Pull Request (PR)

Rebase: Ensure your branch is up-to-date with the main branch. Avoid merge commits if possible.

Write a Clear PR Title: A good PR title quickly explains the contribution (e.g., Feat: Add velocity check V4 or Fix: Lower false positives on Timer check).

Provide a Description:

What problem does this solve?

How did you test it? (Crucially, note if any new check is untested)

Did you follow the AL 2.0 requirements (especially the header requirements)?

Credit for Contributors: All contributors will be credited! The owner (Nolan Sharp) maintains a dedicated list, in an AUTHORS.md file in the repository and there will be a command that will show contributors, to ensure everyone who helps out gets recognition for their work.

Wait for Review: The repository owner (Nolan Sharp) will review your code, check for compatibility, and test it for stability before merging.

Thank you again for helping to improve AvAAntiCheat!

~ Nolan Sharp (project maintainer)
