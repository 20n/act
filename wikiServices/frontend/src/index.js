import React from 'react';
import ReactDOM from 'react-dom';
import App from './App';
import './index.css';
import { Router, Route, hashHistory } from 'react-router';

/* History lesson: https://github.com/ReactTraining/react-router/blob/master/docs/guides/Histories.md
 * For some reason, only hashHistory seems to work. */
ReactDOM.render(
  <Router history={hashHistory}>
    <Route path="/" component={App} />
  </Router>,
  document.getElementById('root')
);
