# CR-20261112-029 导入模板可用性：红色星号 / 必填提示 / 下拉箭头可见

| 项目 | 内容 |
|---|---|
| 提出人 | 用户 2026-11-12 反馈（贴了模板截图）：「*号红色，然后提示每一列每一行都要有，应该有下拉框的就要有下拉列表」 |
| 执行人 | AI 编码助手 |
| 日期 | 2026-11-12 |
| 档位 | **A（微改）**：只动 1 个类（`ExcelTemplateWriter`）+ 其测试；接口路径与请求/响应字段、表结构、权限码、错误码均未变 |
| 关联 | 断言 #94；`ExcelTemplateWriter`（模板生成器） |

## 1. 三个反馈分别对应什么

| 反馈 | 真实原因 | 处理 |
|---|---|---|
| `*` 号要红色 | 表头此前是**纯文本**，做不到"只有星号是红的" | 表头改**富文本**：`任务标题`（黑）+ `*`（红）两段 |
| 提示每一列每一行都要有 | 必填信息只写在「填写须知」页与部分批注里，**数据页看不到** | 必填列表头批注统一以 `【必填】每一行都要填写，不能留空` 开头；须知第 2 条措辞加强 |
| 该有下拉的要有下拉列表 | 下拉**本来就有**，但箭头被隐藏了 | 修 `showDropDown` 语义（见下） |

## 2. 关键坑：OOXML 的 `showDropDown` 语义是反的

排查时先下载了真实模板并解包看 XML，发现：

```xml
<dataValidation type="list" sqref="B2:B20001" ... showDropDown="true">
```

`showDropDown="true"` 在 OOXML 里表示**不显示下拉箭头**（属性名是"抑制箭头"的反向表达）。原代码写的是 `setSuppressDropDownArrow(false)`，POI 直接把它落到该属性上 → 箭头被隐藏 → 用户看到的就是"说好的下拉没有"。

修复：改为 `setSuppressDropDownArrow(true)`，重新生成后 XML 变成 `showDropDown="false"`，箭头可见。

> 这条不是"配置漏了"，而是**语义反直觉**：方法名叫 suppress，传 true 反而显示。已写进断言 #94，避免以后有人"顺手改回 false"。

## 3. 附带的两处改进

- **必填列的下拉带输入提示**：`createPromptBox("必填列", "该列每一行都必须填写，留空会导致整批导入失败")` + `setShowPromptBox(true)`，且必填列 `allowBlank=false`（选填列仍允许留空）。
- **须知措辞**：改为「表头带红色 * 的列为必填，每一行都要有值；有下拉箭头的列请优先从下拉里选」。

> POI 的 `DataValidation` **没有** `setPromptTitle`/`setPrompt`，只有 `createPromptBox(title, text)`——我第一版按直觉写了前两个方法，编译不过，用 `javap` 查了 `poi-5.1.0` 的真实签名才改对。

## 4. 生成的模板实测（解包核对 XML）

| 检查点 | 实际 XML |
|---|---|
| 星号红色 | `<r><rPr><b val="true"/><color indexed="10"/>…</rPr><t>*</t></r>`（`indexed=10` 即红色），且与标题文字是**两个独立 run** |
| 下拉箭头 | `showDropDown="false"`（=显示箭头） |
| 必填提示 | 必填列：`allowBlank="false" showInputMessage="true" promptTitle="必填列" prompt="该列每一行都必须填写…"` |
| 表头批注 | `任务标题：【必填】每一行都要填写，不能留空`；`归属部门：【必填】…；填部门完整路径，如「总部/研发部/前端组」` |

## 5. 测试

`ExcelTemplateWriterTest` 新增 3 条（共 6 条）：

| 用例 | 断言 |
|---|---|
| `requiredMarkIsRed` | 表头是富文本；`*` 在独立字体段且 `getColor() == RED` |
| `requiredColumnsHintEveryRow` | 必填列表头批注含 `【必填】` 与「每一行」 |
| `referenceColumnsHaveVisibleDropdown` | 下拉存在；`getSuppressDropDownArrow() == true`（箭头可见）；必填下拉 `getShowPromptBox() == true` |

最后一条需要一个**"必填的下拉列"**，原测试 schema 里所有列表列都是选填 → 新增 `SCHEMA_WITH_REQUIRED_REF` 专用 schema（否则断言会因为"没有这样的列"而失败——第一版就是这样踩的）。

## 6. 回归范围

| 项 | 命令 | 结果 |
|---|---|---|
| 后端全量 | `mvn -B -pl web -am verify` | **196 用例通过**（common 26 / system 152 / web 18） |
| 模板实测 | 起后端下载模板并解包核对 XML | 三项反馈全部符合 |
| 文档门禁 | `node tools/spec-lint/cli.mjs` | error 0 |

## 7. 遗留

- 该模板的逻辑是**通用**的（`ExcelTemplateWriter` 由 `ExcelSchema` 派生），因此 `posts` 导入模板等其它模板会同时获得这三项改进；本次只实测了「示例任务」模板，其余建议下次点一遍确认。
- 必填的**文本列**（如任务标题）没有下拉，因此没有输入提示框，只有表头批注 + 红 `*` + 须知——这是 Excel 的限制（无 validation 就没有 prompt box）。