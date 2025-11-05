class CCTitle extends HTMLElement{
	static get observedAttributes(){ return ['src','alt']; }
	constructor(){
		super();
		const root = this.attachShadow({mode:'open'});
		root.innerHTML = `
			<style>
				:host{
					display:block;
					top: calc(var(--top, 0px) * var(--scale));
					left: calc(var(--left, 0px) * var(--scale));
					width: calc(var(--w, 600px) * var(--scale));
					height: calc(var(--h, 0px) * var(--scale));
				}
				img{ width:100%; height:100%; object-fit:contain; }
			</style>
			<img class="cc-img" part="img" />
		`;
		this.$img = root.querySelector('img');
	}
	connectedCallback(){ this.#sync(); }
	attributeChangedCallback(){ this.#sync(); }
	#sync(){
		if(this.$img){
			this.$img.src = this.getAttribute('src') || '';
			this.$img.alt = this.getAttribute('alt') || '';
		}
	}
}
customElements.define('cc-title', CCTitle);