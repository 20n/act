import React, { Component } from 'react';
import { Button, Col, Form, FormControl, FormGroup, Grid, InputGroup, Row, Table } from 'react-bootstrap';
import axios from 'axios-es6';
import 'react-router';
import "./App.css";

class App extends Component {

  constructor(props) {
    super(props);
    this.handleTextChange = this.handleTextChange.bind(this);
    this.runSearch = this.runSearch.bind(this);
    console.log("Query params: ", this.props);
    const lastQuery = this.props.location.query == null ? null : this.props.location.query.q;
    this.state = {
      error: null,
      results: null,
      lastQuery: lastQuery,
      searchText: lastQuery,
      searching: false,
    };

    if (lastQuery != null) {
      this.runSearch();
    }
  }

  handleSearch(response) {
    var newState = {lastQuery: response.lastQuery};
    if (response.error == null) {
      newState.results = response.results;
      newState.error = null;
    } else {
      newState.error = response.error;
      newState.results = null;
    }
    this.setState(newState);
  }

  handleTextChange(e) {
    this.setState({searchText : e.target.value});
  }

  updateURL(newQuery) {
    this.props.router.replace({
//      pathname: this.props.location.pathname,
      query: {q: newQuery}
    });
  }

  runSearch(e) {
    /* Prevent weird URL change in Chrome, with help from
     * https://github.com/ReactTraining/react-router/issues/1933#issuecomment-140158983 */
    if (e != null) {
      e.preventDefault();
    }

    const that = this; // TODO: remember how to use `bind` correctly.
    const isNotSearching = !this.state.searching;
    console.log("Is searching: " + isNotSearching);
    if (isNotSearching) {
      this.setState({searching: true});
      const query = this.state.searchText;
      axios.get('/search', {
        params: {q: query},
        timeout: 5000,
        responseType: 'json',
      }).then(function (res) {
        that.updateURL(query);
        that.setState({
          results: res.data,
          lastQuery: query,
          error: null,
        });
      }).catch(function (err) {
        that.setState({
          results: null,
          lastQuery: query,
          error: "We were unable to process your request.  Please contact the system administrator for assistance.",
        });
      }).then(() => new Promise(resolve => setTimeout(resolve, 1000))
      ).then(function () {
        // Always mark the search as complete, even after a failed request.
        that.setState({searching: false});
      });
    }
  }

  renderSearching() {
    return <h2 className="text-center">Searching...</h2>
  }

  renderSearchBox() {
    return (
      <Form>
        <FormGroup>
          <InputGroup>
            <FormControl bsSize="lg" type="text" value={this.state.searchText} placeholder="SMILES" onChange={this.handleTextChange}/>
            <InputGroup.Button>
              <Button type="submit" onClick={this.runSearch}>Search</Button>
            </InputGroup.Button>
          </InputGroup>
        </FormGroup>
      </Form>
    );
  }

  renderResults() {
    if (this.state.results == null || this.state.results.length === 0) {
      return <h2 className="text-center">No results available</h2>;
    }

    const rows = this.state.results.map(function (r, idx) {
      const imageName = r.image_name == null ? <div>No image available</div> : <img src={r.image_name} width="200"/>;
      return <tr key={idx}>
        <td className="col-md-2">{imageName}</td>
        <td className="col-md-10"><a href={r.link}>{r.page_name}</a></td>
      </tr>
    });

    return (
      <div class="container">
        <div className="col-md-2"></div>
        <div className="col-md-8">
          <Table striped>
            <thead><tr><th>Image</th><th>Link</th></tr></thead>
            <tbody>{rows}</tbody>
          </Table>
        </div>
        <div className="col-md-2"></div>
      </div>
    );
  }

  renderError(err) {
    return (
      <h2>An unexpected error occurred.  {this.state.error}</h2>
    );
  }


  render() {
    var contents;
    if (this.state.searching) {
      contents = this.renderSearching();
    } else if (this.state.error != null) {
        contents = this.renderError(this.state.error);
    } else {
        contents = this.renderResults();
    }

    const search = this.renderSearchBox();

    return (
      <div>
        <Grid>
          <Row>
            <Col xs={6} md={4}><img src="static/assets/img/20n.png" className="App-logo"/></Col>
            <Col xs={6} md={4} className="text-center"><h1>Substructure Search</h1></Col><Col xs={6} md={4} />
          </Row>
          <Row>
            <Col xs={4} md={2} /><Col xs={10} md={8} className="text-center">{search}</Col><Col xs={4} md={2} />
          </Row>
        </Grid>
        {contents}
      </div>
    );
  }
}

export default App;
