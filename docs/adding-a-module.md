# Adding a department module

A department tool such as Bench Management, Vendors or Finance is a **module**. Each module has a key, a name, an icon, its own VIEW and EDIT permissions, and the departments it is switched on for. The framework (Expansion Phase X5, package `com.teamops.module`) handles the switch, the sidebar and the access checks. A new module adds its own tables, services and pages, plus a few registrations. Follow the steps below in order.

## How access works

- **Defined in code.** A module is a `ModuleDefinition` bean. `ModuleRegistry` collects these beans. Keys and permissions must be unique across modules, or startup fails.
- **Switched in data.** `department_modules` holds one switch per module per department. A MODULE_MANAGE holder (Super Admin by default) flips it in Admin settings with `PUT /api/admin/modules/{key}/departments/{departmentId}`. The departments a module is switched on for are its owning departments.
- **Who may use it.** A user needs the module's view permission, and the module must be switched on for their primary department or for a department they are a member of. The Super Admin may use every module that is switched on for at least one active department, so switching a module off everywhere turns it off for everyone. Inactive departments don't count.
- **Enforced on every endpoint.** `ModuleAccessInterceptor` checks every request under `/api/modules/{key}/` before it reaches the controller:
  - GET and HEAD need the view permission.
  - Every other method needs the view and edit permissions.
  - Failures return 404 `MODULE_NOT_FOUND`, 403 `FORBIDDEN` or 403 `MODULE_DISABLED`.
  - This check still applies if an endpoint forgets its `@PreAuthorize`.
- **Sidebar.** `GET /api/department-tools` lists the modules the user may open, with `canEdit`. The frontend's "Department tools" section is built from this list.
- **Notifications.** `ModuleAccessService.recipients(key)` returns the users who may open the module. Reminders and notifications go only to them (brief: "Reminders and notifications go only to holders of the module's permission").

## Backend

1. **Migration** (`V{n}__<module>.sql`, in the phase that builds the module):
   - Create the module's tables, following the schema conventions in CLAUDE.md (signed `BIGINT` ids, `DATETIME(6)`, `created_by`/`updated_by`, `deleted_at`/`deleted_by` for records people delete).
   - Insert its two permissions and give them to SUPER_ADMIN only:

     ```sql
     INSERT INTO permissions (code, name, module, description) VALUES
         ('BENCH_VIEW', 'View Bench Management', 'BENCH', 'Open Bench Management: consultants, allocations, bench'),
         ('BENCH_EDIT', 'Edit Bench Management', 'BENCH', 'Change consultants, engagements and allocations');

     INSERT INTO role_permissions (role_id, permission_id)
     SELECT r.id, p.id FROM roles r JOIN permissions p
     WHERE r.code = 'SUPER_ADMIN' AND p.code IN ('BENCH_VIEW', 'BENCH_EDIT');
     ```
   - Optionally switch the module on for its default departments, by department code. Skip a department that doesn't exist; real department data differs between installations:

     ```sql
     INSERT INTO department_modules (module_key, department_id, enabled)
     SELECT 'bench', d.id, TRUE FROM departments d WHERE d.code IN ('HR', 'TA');
     ```

2. **Definition.** Add a bean in the module's package (for example `com.teamops.bench.BenchModule`):

   ```java
   @Configuration(proxyBeanMethods = false)
   public class BenchModule {

   	public static final String KEY = "bench";

   	@Bean
   	ModuleDefinition benchModule() {
   		return new ModuleDefinition(KEY, "Bench Management", "Users",
   				"Consultants, client engagements, allocations and the bench", "BENCH_VIEW", "BENCH_EDIT");
   	}

   }
   ```

   - The key is lower case with dashes. It appears in URLs, so never change it once data exists.
   - The icon is a Lucide v1 icon name.
   - Add the permission codes to `PermissionCodes` when Java code references them.

3. **Controllers.**
   - Map every endpoint under `/api/modules/{key}/`, for example `@RequestMapping("/api/modules/bench/allocations")`. Anything outside that path is not protected by the module switch.
   - Still put `@PreAuthorize("hasAuthority('BENCH_VIEW')")` on the class and `hasAuthority('BENCH_EDIT')` on changes, as CLAUDE.md requires.
   - A POST that only reads (an export or a preview) still needs the edit permission at the interceptor. Make it a GET where possible.

4. **Everything else a module needs** (see CLAUDE.md):
   - Entities extend `BaseEntity`, or `SoftDeletableEntity` with `@SQLRestriction` for records people delete.
   - Map the module's package in `RecordTypes.MODULE_BY_PACKAGE`. `RecordTypesTest` fails until you do.
   - Register each record type's read check in `RecordHistoryAccess`.
   - Add audit actions to `AuditAction` and place them in an `AuditCatalog` module.
   - Write a `RecycleBinHandler` where clean-up is needed.
   - Write `CsvImporter`s for imports, and a `ReportDocument` for exports.
   - Mark money fields `@AuditFinance`.
   - Send reminders through `ModuleAccessService.recipients(KEY)`.
   - If the module is searchable, add a `GlobalSearchService` group. Show results only when the module is in `ModuleAccessService.toolsFor(user)`, which checks both the permission and the switch.

5. **Tests.**
   - Add a `@WebMvcTest` + `@SecuritySliceTest` slice test that mocks `ModuleAccessService`. It shows that the endpoints reject users without the permission (`SliceAuth.EMPLOYEE`, `NOBODY`).
   - Add unit tests for the business rules.
   - Add an `*IT.java` that runs in its own throwaway department. It switches the module on through `/api/admin/modules/{key}/departments/{id}` and checks that a user outside the department gets 403 `MODULE_DISABLED`. `DepartmentModulesIT` shows the pattern.
   - Add the new permissions to `SliceAuth.ALL_PERMISSIONS`.

## Frontend

The X5 frontend provides the "Department tools" sidebar section, built from `GET /api/department-tools`, and the module switches in Admin settings, built from `GET /api/admin/modules`. A new module:

1. Adds its pages under `features/<module>/` and its routes under `/tools/<key>/...`. Register them in `IMPLEMENTED_PAGES` / `detailRoutes`.
2. Shows the sidebar entry only when the module appears in `/api/department-tools`. The page hides edit controls unless `canEdit` is set.
3. Treats a 403 `MODULE_DISABLED` as "This tool is not switched on for your department", not as a generic error.
4. Follows the usual page rules: skeleton, empty and error states; a History tab; undo toast on delete.
