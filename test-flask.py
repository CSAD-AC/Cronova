from flask import Flask, jsonify

app = Flask(__name__)

@app.route('/<id>')
def get_item(id):
    return jsonify({
        "id": id,
        "message": "测试通过"
    })

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8000, debug=True)