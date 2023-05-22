
# Humble Outliner

This is a demo project implementing an outliner view inspired by [Logseq](https://logseq.com/) using [HumbleUI](https://github.com/HumbleUI/HumbleUI).  
It is based on the [humble-starter](https://github.com/lilactown/humble-starter) template.  
The focus of the demo is on the UI, it is not indented for use as it does not store data permanently.

[demo video here]

## Features

- `up/down` go up/down
- `enter` new item
- `backspace` join items
- `tab` indent
- `shift+tab` outdent
- `alt+shift+up/down` move item up/down
- switch between light and dark theme

## Not Implemented

Features they might be interesting to implement as an exercise some day.

- [ ] block-wise selection
- [ ] collapse/expand
- [ ] zoom in/out
- [ ] undo/redo
- [ ] copy/paste
- [ ] dragging
- [ ] text wrapping
- [ ] rich text formatting

## Demo

Run the app with:

```sh
script/run.sh
```

## Development

Run the app including nREPL server:
```sh
script/nrepl.sh
```

Run tests with:
```sh
clojure -M:test
```

Run tests with alternative reporter:
```sh
clj -M:test --reporter kaocha.report/documentation
```

Run tests in watch mode:
```sh
clojure -M:test --watch
```

Generate code coverage report:
```sh
clojure -M:test:coverage
```

### Reloading

To reload the app and see your changes reflected, you can:

1. Evaluate individual forms via the REPL, reset the `state/*app` atom, and then
   call `state/redraw!`
2. Make changes to the files, save them, then call `reload` from the user ns,
   which will use [clojure.tools.namespace](https://github.com/clojure/tools.namespace)
   to detect which ns' should be refreshed, evaluate them, and then call
   `state/redraw!`.

## License & Copyright

Licensed under MIT.  
Copyright Jakub Dundalek 2023.  
Parts of the code Copyright Will Acton 2022.
